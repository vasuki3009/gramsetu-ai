package com.gramsetu.service;

import com.gramsetu.util.LogSafe;
import com.gramsetu.config.TwilioConfig;
import com.gramsetu.dto.DialectResult;
import com.gramsetu.dto.VoiceMessageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

/**
 * End-to-end pipeline for WhatsApp voice messages.
 *
 * Pipeline:
 *   audioUrl (Twilio)
 *     → (1) download audio bytes        [fatal on failure]
 *     → (2) Sarvam STT → transcript     [fatal on failure]
 *     → (3) dialect detection           [falls back to STANDARD, never fatal]
 *     → (4) Groq/Gemini AI answer       [falls back to demo answer, never fatal]
 *     → VoiceMessageResult
 *
 * Audio format note:
 *   WhatsApp voice notes arrive as audio/ogg (Opus codec).
 *   Sarvam STT natively accepts ogg, webm, wav and mp3 — no conversion needed.
 *
 * Reply format:
 *   Voice notes are answered with TEXT only (the transcript plus the dialect-aware answer). No
 *   speech is synthesised for them: the reply is sent as soon as the answer is ready.
 */
@Service
@Slf4j
public class WhatsAppVoiceService {

    @Autowired
    private SarvamSpeechService sarvamService;

    @Autowired
    private DialectDetectionService dialectService;

    @Autowired
    private AIAdvisorService aiService;

    @Autowired
    private TwilioConfig twilioConfig;

    @Value("${twilio.auth.token}")
    private String twilioAuthToken;

    /** Maximum accepted voice-note size in bytes; 0 or less disables the check (unit tests). */
    @Value("${app.limits.max-audio-bytes:2097152}")
    private long maxAudioBytes;

    @Autowired
    private RestTemplate restTemplate;

    private static final String LANGUAGE = "tamil";
    private static final String LOCATION  = "";   // keyword-only detection; no location bias

    /**
     * Processes an incoming WhatsApp voice message end-to-end.
     *
     * @param audioUrl  Twilio MediaUrl (e.g. https://api.twilio.com/2010-04-01/Accounts/AC.../Messages/MM.../Media/ME...)
     * @return          VoiceMessageResult — always non-null; check {@code result.isSuccess()} first
     */
    public VoiceMessageResult processVoiceMessage(String audioUrl) {
        log.info("[VoiceService] Starting voice pipeline");

        // ── Step 1: Download audio from Twilio ────────────────────────────────
        byte[] audioBytes = downloadAudio(audioUrl);
        if (audioBytes == null || audioBytes.length == 0) {
            log.error("[VoiceService] Step 1 FAILED — could not download audio");
            return VoiceMessageResult.builder()
                    .success(false)
                    .failedStep("DOWNLOAD")
                    .answerText("குரல் கோப்பை பதிவிறக்க முடியவில்லை. மீண்டும் முயற்சிக்கவும்.")
                    .build();
        }
        log.info("[VoiceService] Step 1 OK — downloaded {} bytes", audioBytes.length);

        // ── Step 2: Speech-to-Text via Sarvam ────────────────────────────────
        // WhatsApp voice notes are ogg/opus; Sarvam accepts ogg natively.
        String transcript = transcribeAudio(audioBytes);
        if (transcript == null || transcript.isBlank() || isErrorTranscript(transcript)) {
            log.error("[VoiceService] Step 2 FAILED — STT returned unusable transcript ({} chars)", LogSafe.len(transcript));
            return VoiceMessageResult.builder()
                    .success(false)
                    .failedStep("STT")
                    .answerText("குரல் புரியவில்லை. தெளிவாக பேசி மீண்டும் முயற்சிக்கவும்.")
                    .build();
        }
        log.info("[VoiceService] Step 2 OK — transcript: {} chars", transcript.length());

        // ── Step 3: Dialect detection (never fatal) ────────────────────────────
        DialectResult dialect = detectDialect(transcript);
        log.info("[VoiceService] Step 3 OK — dialect={} region={} via={} confidence={}%",
                 dialect.getDialect(), dialect.getDialectRegion(),
                 dialect.getDetectedBy(), dialect.getConfidence());

        // ── Step 4: AI farming advice via Gemini (never fatal) ────────────────
        String answerText = getAiAnswer(transcript, dialect.getDialect());
        log.info("[VoiceService] Step 4 OK — answer length={} chars", answerText.length());

        return VoiceMessageResult.builder()
                .success(true)
                .failedStep(null)
                .transcript(transcript)
                .dialect(dialect.getDialect())
                .dialectRegion(dialect.getDialectRegion())
                .answerText(answerText)
                .build();
    }

    // ── Private step implementations ──────────────────────────────────────────

    /**
     * Step 1 — Downloads audio bytes from a Twilio MediaUrl.
     * Twilio MediaUrls require HTTP Basic auth (account SID + auth token).
     */
    private byte[] downloadAudio(String mediaUrl) {
        try {
            String credentials = twilioConfig.getAccountSid() + ":" + twilioAuthToken;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encoded);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    mediaUrl, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

            byte[] audio = response.getBody();
            if (audio != null && maxAudioBytes > 0 && audio.length > maxAudioBytes) {
                log.warn("[VoiceService] Audio rejected: {} bytes exceeds limit {}", audio.length, maxAudioBytes);
                return null;
            }
            return audio;
        } catch (Exception e) {
            log.error("[VoiceService] Audio download error: {}", LogSafe.error(e));
            return null;
        }
    }

    /**
     * Step 2 — Transcribes audio bytes to Tamil text via Sarvam STT.
     * Uses SarvamSpeechService.toText() which handles ogg, webm, wav, mp3.
     * Actual method: SarvamSpeechService.toText(byte[], String)
     */
    private String transcribeAudio(byte[] audioBytes) {
        try {
            // "audio/ogg" matches WhatsApp voice note format
            return sarvamService.toText(audioBytes, "audio/ogg");
        } catch (Exception e) {
            log.error("[VoiceService] Sarvam STT error: {}", LogSafe.error(e));
            return null;
        }
    }

    /**
     * Step 3 — Detects Tamil dialect from the transcript.
     * Falls back to STANDARD when no keywords match.
     * Actual method: DialectDetectionService.detect(String, String, String)
     */
    private DialectResult detectDialect(String transcript) {
        try {
            return dialectService.detect(transcript, LANGUAGE, LOCATION);
        } catch (Exception e) {
            log.warn("[VoiceService] Dialect detection error (falling back to STANDARD): {}", LogSafe.error(e));
            return DialectResult.standard();
        }
    }

    /**
     * Step 4 — Gets a dialect-aware Tamil farming answer from Gemini via AIAdvisorService.
     * Falls back to demo answer if the AI call fails.
     * Actual method: AIAdvisorService.getAnswer(String, String, String)
     */
    private String getAiAnswer(String question, String dialect) {
        try {
            return aiService.getVoiceAnswer(question, dialect);
        } catch (Exception e) {
            log.error("[VoiceService] AIAdvisorService error (using demo answer): {}", LogSafe.error(e));
            return aiService.getVoiceDemoAnswer();
        }
    }

    /**
     * Returns true when Sarvam STT signals a recognition failure via its error message strings.
     * These come from SarvamSpeechService itself when the API fails or returns no transcript.
     */
    private boolean isErrorTranscript(String transcript) {
        if (transcript == null || transcript.isBlank()) return true;
        if (transcript.length() < 3) return true;
        return transcript.contains("குரல் புரியவில்லை") ||
                transcript.contains("மீண்டும் முயற்சிக்கவும்");
    }
}
