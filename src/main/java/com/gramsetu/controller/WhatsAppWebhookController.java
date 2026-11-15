package com.gramsetu.controller;

import com.gramsetu.config.AsyncConfig;
import com.gramsetu.service.WhatsAppMessageSender;
import com.gramsetu.util.AnswerLanguage;
import com.gramsetu.util.LogSafe;
import com.gramsetu.config.TwilioConfig;
import com.gramsetu.dto.CropResponse;
import com.gramsetu.dto.DialectResult;
import com.gramsetu.repository.CropAdvisoryRepository;
import com.gramsetu.repository.FarmerQueryRepository;
import com.gramsetu.repository.GovernmentSchemeRepository;
import com.gramsetu.service.AIAdvisorService;
import com.gramsetu.service.ChatTurn;
import com.gramsetu.service.CloudinaryService;
import com.gramsetu.service.ConversationMemory;
import com.gramsetu.service.CropVisionService;
import com.gramsetu.service.DialectDetectionService;
import com.gramsetu.service.DialectTranslationService;
import com.gramsetu.service.SarvamSpeechService;
import com.gramsetu.service.WhatsAppVoiceService;
import com.gramsetu.dto.VoiceMessageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import javax.imageio.ImageIO;

@RestController
@RequestMapping("/api/whatsapp")
@Slf4j
public class WhatsAppWebhookController {

    @Autowired
    private AIAdvisorService aiService;

    @Autowired
    private SarvamSpeechService sarvamService;

    @Autowired
    private DialectDetectionService dialectService;

    @Autowired
    private CropVisionService cropVisionService;

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private DialectTranslationService dialectTranslationService;

    @Autowired
    private TwilioConfig twilioConfig;

    @Autowired
    private WhatsAppMessageSender messageSender;

    @Autowired
    @Qualifier(AsyncConfig.AUDIO_REPLY_EXECUTOR)
    private Executor audioReplyExecutor;

    /** Runs the whole message (AI call included) after the HTTP 200 has gone back to Twilio. */
    @Autowired
    @Qualifier(AsyncConfig.MESSAGE_REPLY_EXECUTOR)
    private Executor messageReplyExecutor;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${app.limits.max-question-length:1000}")
    private int maxQuestionLength;

    @Value("${app.limits.max-image-bytes:8388608}")
    private long maxImageBytes;

    /** Full component report on /health. Only the "dev" profile turns this on. */
    @Value("${gramsetu.health.detailed:false}")
    private boolean detailedHealth;

    @Autowired
    private WhatsAppVoiceService voiceService;

    @Autowired
    private FarmerQueryRepository farmerQueryRepo;

    @Autowired
    private CropAdvisoryRepository cropAdvisoryRepo;

    @Autowired
    private GovernmentSchemeRepository schemeRepo;

    @Autowired
    private RestTemplate restTemplate;

    private static final String LANGUAGE = "tamil";
    private static final String LOCATION = "";   // empty → keyword-only detection; no location bias

    /** In-memory dialect chosen by the farmer ("dialect chennai"): phone number → CHENNAI | MADURAI | NELLAI | KONGU | STANDARD. */
    private static final int MAX_DIALECT_PREFS = 10_000;
    private final Map<String, String> userDialectPrefs = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_DIALECT_PREFS;   // evict least-recently-used numbers
                }
            });

    /** In-memory language preference per user (phone number → "tamil" | "english"). Defaults to Tamil. */
    private static final int MAX_LANGUAGE_PREFS = 10_000;
    private final Map<String, String> userLanguagePrefs = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_LANGUAGE_PREFS;   // evict least-recently-used numbers
                }
            });

    /** Last few messages per number (memory only, 30 minutes, bounded LRU). See {@link ConversationMemory}. */
    private final ConversationMemory conversationMemory = new ConversationMemory();

    /**
     * Twilio WhatsApp webhook — receives all incoming messages.
     *
     * Twilio POSTs application/x-www-form-urlencoded with:
     *   Body             — text message (empty for media-only messages)
     *   From             — sender's WhatsApp number (whatsapp:+91XXXXXXXXXX)
     *   NumMedia         — number of attached media files (0 for text)
     *   MediaUrl0        — URL to download the first media file
     *   MediaContentType0 — MIME type of the first media file
     */
    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> handleWebhook(
            @RequestParam("From") String from,
            @RequestParam(value = "Body", defaultValue = "") String body,
            @RequestParam(value = "NumMedia", defaultValue = "0") int numMedia,
            @RequestParam(value = "MediaUrl0", required = false) String mediaUrl,
            @RequestParam(value = "MediaContentType0", required = false) String mediaContentType,
            @RequestParam(value = "MessageSid", required = false) String messageSid) {

        log.info("[WhatsApp] Incoming — sid={} from={} textChars={} numMedia={} contentType={}",
                 messageSid, LogSafe.phone(from), LogSafe.len(body), numMedia, mediaContentType);

        // Twilio gives up on a webhook after about 15 s, and the AI retries can take longer than that.
        // So answer 200 right away and do the work in the background; the reply goes out through sendReply().
        // (The Twilio signature is checked earlier, in TwilioSignatureFilter, before this method runs.)
        try {
            messageReplyExecutor.execute(() -> processMessage(from, body, numMedia, mediaUrl, mediaContentType));
        } catch (RejectedExecutionException e) {
            log.warn("[WhatsApp] Too many messages waiting, asking {} to retry", LogSafe.phone(from));
            sendReply(from, "இப்போது நிறைய செய்திகள் வந்துள்ளன. சிறிது நேரம் கழித்து மீண்டும் அனுப்புங்கள்.");
        }
        return ResponseEntity.ok().build();
    }

    /** The actual work for one incoming message. Runs on {@code messageReplyExecutor}, never on the HTTP thread. */
    private void processMessage(String from, String body, int numMedia, String mediaUrl, String mediaContentType) {
        try {
            if (numMedia > 0 && mediaUrl != null) {
                if (mediaContentType != null && mediaContentType.startsWith("image/")) {
                    // ── Image → CropVisionService → TTS ───────────────────────
                    AudioReply ar = handleImageWithAudio(from, mediaUrl);
                    sendReply(from, ar.text());
                    if (ar.audioUrl() != null) {
                        sendReply(from, "", ar.audioUrl());
                    }
                } else if (mediaContentType != null && mediaContentType.startsWith("audio/")) {
                    // ── Audio → full voice pipeline → text + optional TTS audio ─
                    AudioReply ar = handleAudio(mediaUrl, mediaContentType);
                    sendReply(from, ar.text());
                    if (ar.audioUrl() != null) {
                        sendReply(from, "", ar.audioUrl());
                    }
                } else {
                    log.warn("[WhatsApp] Unsupported media type: {}", mediaContentType);
                    sendReply(from, "மன்னிக்கவும், இந்த கோப்பு வகை ஆதரிக்கப்படவில்லை. " +
                                    "படம் (photo) அல்லது குரல் (voice note) அனுப்பவும்.");
                }
            } else {
                // ── Language preference switch ─────────────────────────────────
                String trimmed = body.trim().toLowerCase();
                if (ConversationMemory.isResetKeyword(body)) {
                    conversationMemory.clear(from);
                    log.info("[WhatsApp] Conversation memory cleared for {}", LogSafe.phone(from));
                    sendReply(from, "சரி, புதிதாகத் தொடங்கலாம். உங்கள் புதிய கேள்வியைக் கேளுங்கள். 🌿");
                } else if (DialectDetectionService.parseOverrideCommand(body).isPresent()) {
                    String chosen = DialectDetectionService.parseOverrideCommand(body).get();
                    userDialectPrefs.put(from, chosen);
                    log.info("[WhatsApp] Dialect override set to {} for {}", chosen, LogSafe.phone(from));
                    sendReply(from, DialectDetectionService.overrideConfirmation(chosen));
                } else if (trimmed.equals("english")) {
                    userLanguagePrefs.put(from, "english");
                    log.info("[WhatsApp] Language set to English for {}", LogSafe.phone(from));
                    sendReply(from, "✅ Language set to *English*. I'll now reply in English!\n\nSend *Tamil* anytime to switch back.");
                } else if (trimmed.equals("tamil") || trimmed.equals("தமிழ்")) {
                    userLanguagePrefs.put(from, "tamil");
                    log.info("[WhatsApp] Language set to Tamil for {}", LogSafe.phone(from));
                    sendReply(from, "✅ மொழி *தமிழ்* ஆக மாற்றப்பட்டது. இனி தமிழில் பதில் சொல்கிறேன்!\n\nஎப்போதும் *English* என்று அனுப்பி மொழியை மாற்றலாம்.");
                } else {
                    // ── Text → DialectDetection → AIAdvisor → TTS ───────────────
                    String userLang = userLanguagePrefs.getOrDefault(from, LANGUAGE);
                    if (body.length() > maxQuestionLength) {
                        log.warn("[WhatsApp] Text rejected: {} chars exceeds limit {}", body.length(), maxQuestionLength);
                        sendReply(from, "english".equals(userLang)
                                ? "Your message is too long (maximum " + maxQuestionLength + " characters). Please shorten it."
                                : "செய்தி மிக நீளமாக உள்ளது. " + maxQuestionLength + " எழுத்துகளுக்குள் அனுப்பவும்.");
                        return;
                    }
                    TextAnswer answered = handleText(from, body, userLang);
                    sendReply(from, answered.replyText());

                    // A blank message only gets the welcome text: no question to answer, so no AI,
                    // speech or upload work for it.
                    if (answered.rawAnswer() == null) {
                        return;
                    }

                    // Send the spoken version in the background so the text arrives instantly. The audio
                    // is built from the answer that was just generated: no second AI call and no second
                    // dialect detection.
                    final String fromAsync = from;
                    final String rawAnswer = answered.rawAnswer();
                    final String dialectCode = answered.dialect();
                    final boolean englishAnswer = "english".equals(answered.language());
                    try {
                        audioReplyExecutor.execute(() -> {
                            try {
                                log.info("[WhatsApp] Building audio from the existing answer ({} chars)", rawAnswer.length());

                                // English: the English voice, no dialect rewriting. Tamil: dialect words and voice.
                                // If speech fails the farmer simply keeps the text reply.
                                String base64Audio;
                                if (englishAnswer) {
                                    base64Audio = sarvamService.toEnglishAudio(rawAnswer);
                                } else {
                                    String dialectAnswerAsync = dialectTranslationService.transformToDialect(rawAnswer, dialectCode);
                                    base64Audio = sarvamService.toAudioLong(dialectAnswerAsync, dialectCode);
                                }
                                if (base64Audio != null && !base64Audio.isBlank()) {
                                    byte[] audioBytes = java.util.Base64.getDecoder().decode(base64Audio);
                                    String filename = "gramsetu_text_" + java.util.UUID.randomUUID();
                                    String audioUrl = cloudinaryService.uploadAudio(audioBytes, filename);
                                    if (audioUrl != null) {
                                        sendReply(fromAsync, "", audioUrl);
                                        log.info("[WhatsApp] Async audio sent to {}", LogSafe.phone(fromAsync));
                                    } else {
                                        log.warn("[WhatsApp] Audio upload failed; the text reply was already delivered");
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("[WhatsApp] Async TTS failed: {}", LogSafe.error(e));
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        log.warn("[WhatsApp] Audio reply skipped: too many pending audio jobs (text reply was sent)");
                    }
                }
            }

        } catch (Exception e) {
            // No message and no stack trace: they could carry the farmer's text.
            log.error("[WhatsApp] Unhandled error for {}: {} (details withheld, they may contain message text)",
                    LogSafe.phone(from), LogSafe.error(e));
            sendReply(from,
                "மன்னிக்கவும், சேவை தற்காலிகமாக கிடைக்கவில்லை. " +
                "சிறிது நேரம் கழித்து மீண்டும் முயற்சிக்கவும்.");
        }
    }

    // ── Health check ───────────────────────────────────────────────────────────

    /**
     * Health check, used by Docker, Railway and uptime monitors. It is public, so by default it reveals
     * nothing but the verdict:
     *   200 {"status":"UP"}     — healthy (a missing optional API key still counts as UP)
     *   503 {"status":"DOWN"}   — the database is unreachable, Twilio is misconfigured or a bean is missing
     * The detailed component report (key flags, database row counts, masked identifiers) is only returned
     * when gramsetu.health.detailed=true, which the "dev" profile sets.
     *
     * Checks performed (all fast, no external API calls):
     *   • Twilio    — SID/token configured and correctly formatted
     *   • Sarvam    — API key present and non-placeholder
     *   • Gemini    — API key present and non-placeholder
     *   • Database  — live row-count queries on all three tables
     *   • Services  — all five Spring beans successfully injected
     *
     * Overall status:
     *   UP       — every component healthy
     *   DEGRADED — non-critical component unavailable (API key missing)
     *   DOWN     — database unreachable or critical bean missing
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("[WhatsApp] Health check requested");

        Map<String, Object> response = new LinkedHashMap<>();
        boolean anyDown     = false;
        boolean anyDegraded = false;

        // ── Twilio ────────────────────────────────────────────────────────────
        Map<String, Object> twilioStatus = new LinkedHashMap<>();
        try {
            String sid   = twilioConfig.getAccountSid();
            String from  = twilioConfig.getWhatsappFrom();
            boolean sidOk  = sid  != null && sid.startsWith("AC") && sid.length() == 34;
            boolean fromOk = from != null && from.startsWith("whatsapp:");
            boolean tokenOk = authToken != null && authToken.length() >= 32;

            if (sidOk && fromOk && tokenOk) {
                twilioStatus.put("status", "UP");
                twilioStatus.put("accountSid", sid.substring(0, 6) + "****" + sid.substring(sid.length() - 4));
                twilioStatus.put("whatsappFrom", from);
                twilioStatus.put("message", "Credentials configured and valid format");
            } else {
                twilioStatus.put("status", "DOWN");
                twilioStatus.put("accountSid", sidOk ? "valid" : "missing or wrong format");
                twilioStatus.put("authToken", tokenOk ? "valid" : "missing or too short");
                twilioStatus.put("whatsappFrom", fromOk ? "valid" : "missing or wrong format");
                twilioStatus.put("message", "One or more Twilio credentials are invalid");
                anyDown = true;
            }
        } catch (Exception e) {
            twilioStatus.put("status", "DOWN");
            twilioStatus.put("message", "TwilioConfig bean error: " + e.getMessage());
            anyDown = true;
        }

        // ── Sarvam ────────────────────────────────────────────────────────────
        Map<String, Object> sarvamStatus = new LinkedHashMap<>();
        try {
            boolean configured = sarvamService.isKeyConfigured();
            sarvamStatus.put("status", configured ? "UP" : "DEGRADED");
            sarvamStatus.put("keyConfigured", configured);
            sarvamStatus.put("sttEndpoint", "https://api.sarvam.ai/speech-to-text");
            sarvamStatus.put("ttsEndpoint", "https://api.sarvam.ai/text-to-speech");
            sarvamStatus.put("message", configured
                    ? "API key present — STT (" + sarvamService.getSttModel() + ") and TTS (" + sarvamService.getTtsModel() + ") ready"
                    : "SARVAM_API_KEY not set — voice features will use fallback");
            if (!configured) anyDegraded = true;
        } catch (Exception e) {
            sarvamStatus.put("status", "DOWN");
            sarvamStatus.put("message", "SarvamSpeechService bean error: " + e.getMessage());
            anyDown = true;
        }

        // ── Gemini ────────────────────────────────────────────────────────────
        Map<String, Object> geminiStatus = new LinkedHashMap<>();
        try {
            boolean configured = aiService.isKeyConfigured();
            geminiStatus.put("status", configured ? "UP" : "DEGRADED");
            geminiStatus.put("keyConfigured", configured);
            geminiStatus.put("model", aiService.getGeminiModel());
            geminiStatus.put("message", configured
                    ? "API key present — AI advice and crop vision ready"
                    : "GEMINI_API_KEY not set — responses will use demo fallback");
            if (!configured) anyDegraded = true;
        } catch (Exception e) {
            geminiStatus.put("status", "DOWN");
            geminiStatus.put("message", "AIAdvisorService bean error: " + e.getMessage());
            anyDown = true;
        }

        // ── Database ──────────────────────────────────────────────────────────
        Map<String, Object> dbStatus = new LinkedHashMap<>();
        try {
            long queries    = farmerQueryRepo.count();
            long advisories = cropAdvisoryRepo.count();
            long schemes    = schemeRepo.count();
            dbStatus.put("status", "UP");
            dbStatus.put("farmerQueries", queries);
            dbStatus.put("cropAdvisories", advisories);
            dbStatus.put("governmentSchemes", schemes);
            dbStatus.put("message", "MySQL connected — " + (queries + advisories + schemes) + " total rows");
        } catch (Exception e) {
            dbStatus.put("status", "DOWN");
            dbStatus.put("message", "Database unreachable: " + e.getMessage());
            anyDown = true;
        }

        // ── Service beans ─────────────────────────────────────────────────────
        Map<String, Object> beansStatus = new LinkedHashMap<>();
        boolean allBeansOk =
                aiService      != null &&
                sarvamService  != null &&
                dialectService != null &&
                cropVisionService != null &&
                voiceService   != null;

        beansStatus.put("status", allBeansOk ? "UP" : "DOWN");
        beansStatus.put("aiAdvisorService",       aiService      != null ? "INITIALIZED" : "MISSING");
        beansStatus.put("sarvamSpeechService",    sarvamService  != null ? "INITIALIZED" : "MISSING");
        beansStatus.put("dialectDetectionService",dialectService != null ? "INITIALIZED" : "MISSING");
        beansStatus.put("cropVisionService",      cropVisionService != null ? "INITIALIZED" : "MISSING");
        beansStatus.put("whatsAppVoiceService",   voiceService   != null ? "INITIALIZED" : "MISSING");
        beansStatus.put("twilioConfig",           twilioConfig   != null ? "INITIALIZED" : "MISSING");

        if (!allBeansOk) anyDown = true;

        // ── Assemble response ─────────────────────────────────────────────────
        String overallStatus = anyDown ? "DOWN" : (anyDegraded ? "DEGRADED" : "UP");
        response.put("status", overallStatus);
        response.put("timestamp", Instant.now().toString());
        response.put("service", "GramSetu WhatsApp Integration");

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("twilio",   twilioStatus);
        components.put("sarvam",   sarvamStatus);
        components.put("gemini",   geminiStatus);
        components.put("database", dbStatus);
        components.put("services", beansStatus);
        response.put("components", components);

        HttpStatus httpStatus = anyDown ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        log.debug("[WhatsApp] Health check complete — overall={}", overallStatus);

        if (!detailedHealth) {
            // Public view: just the verdict that orchestrators and monitors need.
            return ResponseEntity.status(httpStatus).body(Map.of("status", anyDown ? "DOWN" : "UP"));
        }
        return ResponseEntity.status(httpStatus).body(response);
    }

    // ── Message type handlers ──────────────────────────────────────────────────

    /**
     * Text message: detect dialect, get AI farming advice, synthesise TTS audio.
     * Returns AudioReply with the text answer and a public URL to the WAV audio clip.
     * Falls back to text-only (audioUrl = null) if Sarvam TTS is unavailable.
     */
    private TextAnswer handleText(String from, String body, String preference) {
        if (body == null || body.isBlank()) {
            return new TextAnswer(
                "வணக்கம்! 🌿 GramSetu உங்கள் வேளாண் AI உதவியாளர்.\n\n" +
                "• கேள்வியை தட்டச்சு செய்யவும்\n" +
                "• பயிர் படம் அனுப்பவும் (நோய் கண்டறிதல்)\n" +
                "• குரல் குறிப்பு அனுப்பவும்",
                null, null, null
            );
        }

        // The question's script decides (Tamil script: Tamil; clearly English: English). Only when the text is unclear
        // ("ok", numbers, Tamil typed in English letters) does the saved english / tamil command decide.
        String language = AnswerLanguage.forWhatsApp(preference, body);
        log.info("[WhatsApp] Handling text (lang={}, {} chars)", language, LogSafe.len(body));
        // A dialect the farmer chose wins over detection (English replies have no dialect).
        String chosen = userDialectPrefs.get(from);
        DialectResult dialect = chosen != null && !"english".equals(language)
                ? DialectDetectionService.overrideResult(chosen)
                : dialectService.detect(body, language, LOCATION);

        String dialectLabel = switch (dialect.getDialect().toUpperCase()) {
            case "KONGU"   -> "🗣️ *கொங்கு தமிழ்* 🌾\n\n";
            case "MADURAI" -> "🗣️ *மதுரை தமிழ்* 🏛️\n\n";
            case "NELLAI"  -> "🗣️ *நெல்லை தமிழ்* 🌴\n\n";
            case "CHENNAI" -> "🗣️ *சென்னை தமிழ்* 🏙️\n\n";
            default        -> "";
        };

        List<ChatTurn> history = conversationMemory.history(from);
        String answer = aiService.getAnswer(body, language, dialect.getDialect(), history);
        // Greetings and sample/fallback answers say nothing about the farmer's crop, so they are not remembered.
        if (!ConversationMemory.isGreeting(body) && !AIAdvisorService.isFallbackAnswer(answer)) {
            conversationMemory.remember(from, body, answer);
        }
        String formattedAnswer = formatVoiceResponse(answer);

        // Send text reply IMMEDIATELY — don't wait for TTS
        return new TextAnswer(dialectLabel + formattedAnswer, answer, dialect.getDialect(), language);
    }

    /** Carries both the WhatsApp text reply and an optional TTS audio URL. */
    private record AudioReply(String text, String audioUrl) {}

    /** Result of answering a text message: what to send now, plus what the audio is built from. */
    private record TextAnswer(String replyText, String rawAnswer, String dialect, String language) {}

    /**
     * Voice message: delegates to WhatsAppVoiceService for the full pipeline
     * (download → Sarvam STT → dialect detect → Gemini AI → Sarvam TTS).
     *
     * When TTS succeeds, the WAV bytes are stored in-memory and a public URL
     * is returned so Twilio can fetch and deliver the audio to WhatsApp.
     * Falls back to text-only reply when TTS fails or API key is missing.
     */
    private AudioReply handleAudio(String mediaUrl, String mediaContentType) {
        log.info("[WhatsApp] Handling audio: type={}", mediaContentType);

        VoiceMessageResult result = voiceService.processVoiceMessage(mediaUrl);

        if (!result.isSuccess()) {
            String errorMsg = switch (result.getFailedStep()) {
                case "DOWNLOAD" -> "குரல் கோப்பை பதிவிறக்க முடியவில்லை. மீண்டும் முயற்சிக்கவும்.";
                case "STT"      -> "குரல் புரியவில்லை. தெளிவாக பேசி மீண்டும் முயற்சிக்கவும்.";
                default         -> "குரல் செய்தியை செயல்படுத்த முடியவில்லை. மீண்டும் முயற்சிக்கவும்.";
            };
            return new AudioReply(errorMsg, null);
        }

        log.info("[WhatsApp] Voice pipeline OK — dialect={} transcriptChars={}",
                 result.getDialect(), LogSafe.len(result.getTranscript()));

        // Apply dialect transformation then re-generate TTS with dialect-matched voice
        String answer = result.getAnswerText();
        String dialectAnswer = dialectTranslationService.transformToDialect(answer, result.getDialect());
        log.info("[WhatsApp] Voice dialect transformation: {} → {} chars", answer.length(), dialectAnswer.length());

        String dialectLabel = switch (result.getDialect() == null ? "" : result.getDialect().toUpperCase()) {
            case "KONGU"   -> "🗣️ *கொங்கு தமிழ்* 🌾\n\n";
            case "MADURAI" -> "🗣️ *மதுரை தமிழ்* 🏛️\n\n";
            case "NELLAI"  -> "🗣️ *நெல்லை தமிழ்* 🌴\n\n";
            case "CHENNAI" -> "🗣️ *சென்னை தமிழ்* 🏙️\n\n";
            default        -> "";
        };

        // Skip TTS entirely — text reply is instant
        String ttsAudioUrl = null;

        String text = "📝 *நீங்கள் சொன்னது:*\n_" + result.getTranscript() + "_\n\n"
                + dialectLabel + formatVoiceResponse(answer);
        log.info("[WhatsApp] Final voice reply ({} chars)", text.length());
        return new AudioReply(text, ttsAudioUrl);
    }

    private AudioReply handleImageWithAudio(String from, String mediaUrl) {
        log.info("[WhatsApp] Handling image for crop disease detection");

        byte[] imageBytes = downloadMedia(mediaUrl);
        if (imageBytes == null) {
            return new AudioReply("படத்தை பதிவிறக்க முடியவில்லை. மீண்டும் முயற்சிக்கவும்.", null);
        }
        log.info("[WhatsApp] Image downloaded: {} bytes", imageBytes.length);

        // Resize image to 1024px max dimension to speed up Gemini processing
        imageBytes = resizeImage(imageBytes, 1024);

        CropResponse result = cropVisionService.analyzeCropImage(imageBytes, LANGUAGE);
        log.info("[WhatsApp] Crop diagnosis complete — verified={} source={}",
                result.isVerified(), result.getVerifiedSource());

        String diagnosis = formatCropResponse(result);

        String ttsAudioUrl = null;
        try {
            log.info("[WhatsApp] Generating TTS for crop diagnosis...");
            String detectedDialect = "kongu";

            // Build TTS summary — disease + severity + all treatment + prevention
            String disease = extractSection(result.getDiagnosis(), "நோய்:", "நோய் :");
            String severity = inferSeverity(result.getDiagnosis());
            String treatment = extractSection(result.getDiagnosis(), "சிகிச்சை:", "சிகிச்சை :");
            String prevention = extractSection(result.getDiagnosis(), "தடுப்பு முறைகள்:", "தடுப்பு முறைகள் :");

            String ttsText = "நோய் கண்டறியப்பட்டது. " +
                    (!disease.isEmpty() ? "நோய்: " + disease + ". " : "") +
                    "தீவிரம்: " + severity.replaceAll("[🔴🟡🟢]", "").trim() + ". " +
                    (!treatment.isEmpty() ? "சிகிச்சை முறை: " + treatment.replaceAll("\n", ". ") + ". " : "") +
                    (!prevention.isEmpty() ? "தடுப்பு முறை: " + prevention.replaceAll("\n", ". ") : "");

            log.info("[WhatsApp] TTS text ({} chars)", ttsText.length());

            String dialectTts = dialectTranslationService.transformToDialect(ttsText, detectedDialect);
            String base64Audio = sarvamService.toAudioLong(dialectTts, detectedDialect);

            if (base64Audio != null && !base64Audio.isBlank()) {
                byte[] audioBytes = Base64.getDecoder().decode(base64Audio);
                String filename = "gramsetu_diagnosis_" + UUID.randomUUID();
                ttsAudioUrl = cloudinaryService.uploadAudio(audioBytes, filename);
                log.info("[WhatsApp] Diagnosis TTS uploaded to Cloudinary");
            }
        } catch (Exception e) {
            log.warn("[WhatsApp] TTS generation failed: {}", LogSafe.error(e));
        }

        return new AudioReply(diagnosis, ttsAudioUrl);
    }

    private byte[] resizeImage(byte[] imageBytes, int maxDimension) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            int width = original.getWidth();
            int height = original.getHeight();

            if (width <= maxDimension && height <= maxDimension) {
                return imageBytes; // Already small enough
            }

            double scale = (double) maxDimension / Math.max(width, height);
            int newWidth = (int) (width * scale);
            int newHeight = (int) (height * scale);

            BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.drawImage(original, 0, 0, newWidth, newHeight, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("[WhatsApp] Image resize failed, using original: {}", LogSafe.error(e));
            return imageBytes;
        }
    }

    private String formatVoiceResponse(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        return raw
            .replace("காரணங்கள்:",         "🔍 *காரணங்கள்:*")
            .replace("தீர்வுகள்:",          "✅ *தீர்வுகள்:*")
            .replace("இன்னைக்கே செய்யுங்க:", "⚡ *இன்னைக்கே செய்யுங்க:*")
            .replace("இன்னைக்கே செய்யுங்க",  "⚡ *இன்னைக்கே செய்யுங்க*")
            .replace("அடுத்து கேளுங்கள்:",   "💬 *அடுத்து கேளுங்கள்:*")
            .replace("அடுத்து கேளுங்கள்",    "💬 *அடுத்து கேளுங்கள்*")
            .replaceAll("•\\s([^:\\n]+):", "• *$1:*");
    }

    /**
     * Parses the structured Gemini diagnosis text and builds a clean,
     * section-by-section WhatsApp reply in Tamil.
     *
     * WhatsApp supports *bold* via asterisks. Sections are separated by
     * blank lines so the message is easy to read on a small screen.
     */
    private String formatCropResponse(CropResponse result) {
        String raw = result.getDiagnosis();

        String disease    = extractSection(raw, "நோய்:", "நோய் :");
        String symptoms   = extractSection(raw, "அறிகுறிகள்:", "அறிகுறிகள் :");
        String treatment  = extractSection(raw, "சிகிச்சை:", "சிகிச்சை :");
        String prevention = extractSection(raw, "தடுப்பு முறைகள்:", "தடுப்பு முறைகள் :");
        String medicine   = extractSection(raw, "மருந்து:", "மருந்து :");

        String severity = inferSeverity(raw);

        StringBuilder sb = new StringBuilder();
        sb.append("🌿 *பயிர் நோய் கண்டறிதல்*\n");
        sb.append("─────────────────────\n\n");

        // Disease name
        if (!disease.isEmpty()) {
            sb.append("🦠 *நோய்:* ").append(disease).append("\n");
        }

        // Severity badge
        sb.append("⚠️ *தீவிரம்:* ").append(severity).append("\n\n");

        // Treatment steps
        if (!treatment.isEmpty()) {
            sb.append("💊 *சிகிச்சை:*\n").append(treatment).append("\n\n");
        }

        // Prevention tips
        if (!prevention.isEmpty()) {
            sb.append("🛡️ *தடுப்பு முறைகள்:*\n").append(prevention).append("\n\n");
        }

        // Medicine recommendation
        if (!medicine.isEmpty()) {
            sb.append("💉 *மருந்து:* ").append(medicine).append("\n");
        }

        // Verified source badge
        if (result.isVerified()) {
            sb.append("\n✅ *ஆதாரம்:* ").append(result.getVerifiedSource());
        }

        // Fall back to raw diagnosis if parsing extracted nothing useful
        if (disease.isEmpty() && treatment.isEmpty()) {
            log.warn("[WhatsApp] Section parsing found nothing — returning raw diagnosis");
            return "🌿 *பயிர் நோய் கண்டறிதல்*\n\n" + raw;
        }

        return sb.toString().trim();
    }

    /**
     * Extracts the text that follows one of the given section labels up to
     * the next recognised section label (or end of string).
     *
     * Handles both "நோய்:" and "நோய் :" spacing variants.
     * Returns an empty string when the label is not found.
     */
    private String extractSection(String text, String... labels) {
        // All Tamil section headers we know about — used as stop markers
        String[] allHeaders = {
            "நோய்:", "நோய் :",
            "அறிகுறிகள்:", "அறிகுறிகள் :",
            "காரணம்:", "காரணம் :",
            "சிகிச்சை:", "சிகிச்சை :",
            "தடுப்பு முறைகள்:", "தடுப்பு முறைகள் :",
            "மருந்து:", "மருந்து :"
        };

        for (String label : labels) {
            int start = text.indexOf(label);
            if (start == -1) continue;

            int contentStart = start + label.length();
            int end = text.length();

            // Find the earliest stop marker that comes after contentStart
            for (String header : allHeaders) {
                int headerPos = text.indexOf(header, contentStart);
                if (headerPos != -1 && headerPos < end) {
                    end = headerPos;
                }
            }

            String extracted = text.substring(contentStart, end).strip();
            if (!extracted.isEmpty()) return extracted;
        }
        return "";
    }

    /**
     * Infers a Tamil severity label by scanning the diagnosis text for
     * indicator words. Returns one of: கடுமையானது / மிதமானது / லேசானது.
     */
    private String inferSeverity(String diagnosis) {
        String lower = diagnosis.toLowerCase();

        // Severe indicators
        if (lower.contains("கடுமையான")  || lower.contains("அதிக பாதிப்பு") ||
            lower.contains("முழுவதும்")  || lower.contains("உடனடியாக அகற்று") ||
            lower.contains("விரைவில்")   || lower.contains("பரவி")) {
            return "கடுமையானது 🔴";
        }

        // Mild indicators
        if (lower.contains("லேசான")      || lower.contains("ஆரம்ப நிலை") ||
            lower.contains("சில இடங்கள்") || lower.contains("குறைந்த அளவு")) {
            return "லேசானது 🟢";
        }

        // Default — moderate
        return "மிதமானது 🟡";
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /**
     * Downloads a Twilio media file using Basic auth (account SID + auth token).
     * Returns null on failure so callers can send a Tamil error message instead.
     */
    private byte[] downloadMedia(String mediaUrl) {
        try {
            String credentials = twilioConfig.getAccountSid() + ":" + authToken;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encoded);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    mediaUrl, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

            byte[] media = response.getBody();
            log.info("[WhatsApp] Downloaded {} bytes of media", media != null ? media.length : 0);
            if (media != null && maxImageBytes > 0 && media.length > maxImageBytes) {
                log.warn("[WhatsApp] Media rejected: {} bytes exceeds limit {}", media.length, maxImageBytes);
                return null;
            }
            return media;

        } catch (Exception e) {
            log.error("[WhatsApp] Media download failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Sends a WhatsApp text reply via Twilio Message API.
     * Logs but does not throw on failure — Twilio must always receive 200 OK.
     */
    private void sendReply(String to, String text) {
        sendReply(to, text, null);
    }

    /**
     * Sends a WhatsApp reply with optional audio media via Twilio Message API.
     *
     * When {@code audioUrl} is non-null, Twilio fetches the audio from that URL
     * and delivers it as a WhatsApp voice message alongside the text caption.
     * The URL must be publicly accessible (use ngrok in development).
     *
     * Logs but does not throw on failure — Twilio must always receive 200 OK.
     */
    private void sendReply(String to, String text, String audioUrl) {
        messageSender.send(to, text, audioUrl);
    }
}
