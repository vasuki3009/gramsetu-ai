package com.gramsetu.service;

import com.gramsetu.util.AdvisorPrompts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Text answers from the configured AI provider (AI_PROVIDER: gemini | groq, default gemini).
 * The chosen provider is tried first, the other one (if its key is set) is the fallback,
 * and built-in sample answers are the last resort. The system prompt lives in {@link AdvisorPrompts}.
 */
@Service
@Slf4j
public class AIAdvisorService {

    @Value("${gemini.api.key}")
    private String apiKey;

    /** Default Gemini model. Override with GEMINI_MODEL. (gemini-2.0-flash was retired on 1 June 2026.) */
    public static final String DEFAULT_GEMINI_MODEL = "gemini-3.8-flash";

    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.3-70b-versatile";

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${gemini.model:" + DEFAULT_GEMINI_MODEL + "}")
    private String geminiModel = DEFAULT_GEMINI_MODEL;

    @Value("${ai.provider:gemini}")
    private String providerSetting = "gemini";

    /** Low temperature: farm advice should be steady and repeatable, not creative. */
    @Value("${ai.temperature:0.2}")
    private double temperature = 0.2;

    /** Ceiling for one answer. Generous because Tamil uses many tokens and Gemini 3 "thinking" counts against it. */
    @Value("${ai.max-output-tokens:2048}")
    private int maxOutputTokens = 2048;

    /** Models tried after the primary one keeps failing (comma-separated). Empty means none. */
    @Value("${gemini.fallback-models:gemini-3.6-flash,gemini-3.5-flash-lite}")
    private String fallbackModels = "gemini-3.6-flash,gemini-3.5-flash-lite";

    /** Attempts per model for transient HTTP errors (429, 5xx). A timeout is not retried: the next model is tried instead. */
    @Value("${ai.retry.max-attempts:2}")
    private int maxAttempts = 2;

    /** Read timeout of one call. Long on purpose: Tamil answers are slow, and WhatsApp replies are sent in the background. */
    @Value("${ai.read-timeout-ms:25000}")
    private int readTimeoutMs = 25_000;

    /**
     * Gemini thinking level (AI_THINKING_LEVEL). "low" is the lowest level every current Flash model accepts
     * (gemini-3.8-flash does not accept "minimal"). Empty means: send no thinking setting, use the model default.
     */
    @Value("${ai.thinking-level:low}")
    private String thinkingLevel = "low";

    /** First backoff; it doubles each retry (1 s, then 2 s). */
    @Value("${ai.retry.base-backoff-ms:1000}")
    private long baseBackoffMs = 1000;

    /**
     * After this much time in total no RETRY is started. Every model in the chain still gets its first attempt,
     * so the budget can never skip the fallback models.
     */
    @Value("${ai.retry.budget-ms:60000}")
    private long budgetMs = 60_000;

    /** Shown before the built-in sample answer, so it is never mistaken for a real AI answer. */
    public static final String BUSY_NOTE_TAMIL = "இப்போது AI பிஸியாக உள்ளது, சிறிது நேரம் கழித்து மீண்டும் கேளுங்கள்";
    static final String BUSY_NOTE_ENGLISH = "The AI is busy right now. Please ask again in a little while.";

    // replaceable in tests
    LongConsumer sleeper = AIAdvisorService::sleepQuietly;
    LongSupplier clock = System::currentTimeMillis;

    private final RestTemplate restTemplate;
    private final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

    public AIAdvisorService() {
        requestFactory.setConnectTimeout(10_000);  // 10 seconds to connect
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    /** Applies AI_READ_TIMEOUT_MS once the configuration has been injected. */
    @PostConstruct
    void applyReadTimeout() {
        requestFactory.setReadTimeout(readTimeoutMs);
    }

    public boolean isKeyConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && !apiKey.toUpperCase().startsWith("YOUR_");
    }

    private boolean isGroqConfigured() {
        return groqApiKey != null && !groqApiKey.isBlank();
    }

    /** The Gemini model in use (health report, crop vision). */
    public String getGeminiModel() {
        return geminiModel == null || geminiModel.isBlank() ? DEFAULT_GEMINI_MODEL : geminiModel.trim();
    }

    public AiProvider getProvider() {
        return AiProvider.parse(providerSetting);
    }

    // ── Public entry points ────────────────────────────────────────────────

    /** General-purpose call (schemes, crop calendar). Always returns text; sample answer if no provider works. */
    public String callGemini(String prompt) {
        return generate(AdvisorPrompts.systemPrompt("tamil", null), prompt)
                .orElseGet(() -> sampleAnswer(prompt, "tamil"));
    }

    /** Kept for existing callers; the temperature is now one configured value (AI_TEMPERATURE). */
    public String callGemini(String prompt, double ignoredTemperature) {
        return callGemini(prompt);
    }

    public String getAnswer(String prompt, String language) {
        return getAnswer(prompt, language, "STANDARD");
    }

    /** The farmer's question (it may already carry a short dialect hint) answered under the system prompt. */
    public String getAnswer(String prompt, String language, String dialect) {
        String system = AdvisorPrompts.systemPrompt(language, dialect);
        Optional<String> answer = generate(system, prompt);
        if (answer.isPresent()) {
            log.info("[Text] Using AI response ({} chars)", answer.get().length());
            return answer.get();
        }
        log.warn("[Text] No AI provider answered, using sample answer");
        return sampleAnswer(prompt, "english".equals(language) ? "english" : "tamil");
    }

    /**
     * Like {@link #getAnswer(String, String, String)}, but the earlier messages of the conversation are sent as prior
     * turns, so a follow-up keeps its context. {@code history} may be empty or null.
     */
    public String getAnswer(String prompt, String language, String dialect, List<ChatTurn> history) {
        String system = AdvisorPrompts.systemPrompt(language, dialect);
        Optional<String> answer = generate(system, history, prompt);
        if (answer.isPresent()) {
            log.info("[Text] Using AI response ({} chars, {} earlier messages)", answer.get().length(),
                    history == null ? 0 : history.size());
            return answer.get();
        }
        log.warn("[Text] No AI provider answered, using sample answer");
        return sampleAnswer(prompt, "english".equals(language) ? "english" : "tamil");
    }

    /** True when the text is a built-in sample/fallback answer (it starts with the busy note), not a real AI answer. */
    public static boolean isFallbackAnswer(String answer) {
        return answer != null && (answer.startsWith(BUSY_NOTE_TAMIL) || answer.startsWith(BUSY_NOTE_ENGLISH));
    }

    /** Voice / WhatsApp answers follow the same rules as text answers. */
    public String getVoiceAnswer(String question, String dialect) {
        log.info("[Voice] Asking AI (len={}, dialect={})", question.length(), dialect);
        Optional<String> answer = generate(AdvisorPrompts.systemPrompt("tamil", dialect), question);
        if (answer.isPresent()) {
            log.info("[Voice] AI OK ({} chars)", answer.get().length());
            return answer.get();
        }
        log.warn("[Voice] No AI provider answered, using sample answer");
        return sampleAnswer(question, "tamil");
    }

    /** A short 2-sentence summary for text-to-speech, kept under about 250 characters. */
    public String getAudioSummary(String question, String dialect) {
        String prompt = "Answer this question in at most 2 short sentences and under 200 characters: "
                + "first the main reason, then the first thing to do. Question: \"" + question + "\"";
        log.info("[Audio] Generating short audio summary (dialect={})", dialect);
        String response = generate(AdvisorPrompts.systemPrompt("tamil", dialect), prompt).orElse("");

        if (response.length() > 250) {
            for (int i = 249; i >= 0; i--) {
                if (response.charAt(i) == '.' || response.charAt(i) == '\n') {
                    return response.substring(0, i + 1).trim();
                }
            }
            return response.substring(0, 250);
        }
        return response;
    }

    // ── Provider chain with retries ────────────────────────────────────────

    /** One place to call: a Gemini model, or Groq. */
    record Target(AiProvider provider, String model) {
        String label() { return provider == AiProvider.GEMINI ? "gemini " + model : "groq " + model; }
    }

    /**
     * The calls to try, in order: for Gemini the primary model then GEMINI_FALLBACK_MODELS, for Groq its one model.
     * AI_PROVIDER decides which provider comes first; a provider without a key is left out.
     */
    List<Target> buildChain() {
        List<Target> chain = new ArrayList<>();
        for (AiProvider p : AiProvider.callOrder(getProvider(), isKeyConfigured(), isGroqConfigured())) {
            if (p == AiProvider.GEMINI) {
                for (String m : geminiModels()) chain.add(new Target(AiProvider.GEMINI, m));
            } else {
                chain.add(new Target(AiProvider.GROQ, GROQ_MODEL));
            }
        }
        return chain;
    }

    /** Primary model first, then the fallback models, without blanks or duplicates. */
    List<String> geminiModels() {
        Set<String> models = new LinkedHashSet<>();
        models.add(getGeminiModel());
        if (fallbackModels != null) {
            for (String m : fallbackModels.split(",")) {
                if (!m.isBlank()) models.add(m.trim());
            }
        }
        return new ArrayList<>(models);
    }

    /**
     * Tries every target in the chain. A 429 or 5xx is retried on the same target up to {@code maxAttempts} times with
     * exponential backoff (1 s, 2 s, ...) while the time budget lasts. A timeout or any other failure (such as 400)
     * moves straight on to the next target. Every target gets at least its first attempt, whatever the budget says.
     * Empty when nothing is configured or every target fails.
     */
    private Optional<String> generate(String systemPrompt, String userPrompt) {
        return generate(systemPrompt, List.of(), userPrompt);
    }

    private Optional<String> generate(String systemPrompt, List<ChatTurn> history, String userPrompt) {
        long deadline = nowMs() + budgetMs;
        for (Target target : buildChain()) {
            Optional<String> text = tryTarget(target, systemPrompt, history, userPrompt, deadline);
            if (text.isPresent()) return text;
        }
        return Optional.empty();
    }

    private Optional<String> tryTarget(Target target, String systemPrompt, List<ChatTurn> history, String userPrompt,
                                       long deadline) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String text = target.provider() == AiProvider.GEMINI
                        ? callGeminiApi(target.model(), systemPrompt, history, userPrompt)
                        : callGroqApi(systemPrompt, history, userPrompt);
                if (text != null && !text.isBlank()) {
                    log.info("[AI] {} attempt {}/{}: 200 OK", target.label(), attempt, maxAttempts);
                    return Optional.of(text.trim());
                }
                log.warn("[AI] {} attempt {}/{}: 200 but empty or blocked answer", target.label(), attempt, maxAttempts);
                return Optional.empty();   // an empty answer will not improve by repeating the same request
            } catch (Exception e) {
                boolean retryable = isRetryable(e);
                long backoff = baseBackoffMs * (1L << (attempt - 1));   // 1 s, 2 s, ...
                boolean willRetry = retryable && attempt < maxAttempts && nowMs() + backoff < deadline;
                String next;
                if (willRetry) next = ", retrying in " + backoff + " ms";
                else if (isTimeout(e)) next = ", trying the next model";
                else if (retryable) next = ", giving up on this model";
                else next = ", not retrying";
                log.warn("[AI] {} attempt {}/{}: {}{}", target.label(), attempt, maxAttempts, describe(e), next);
                if (!willRetry) return Optional.empty();
                sleeper.accept(backoff);
            }
        }
        return Optional.empty();
    }

    /** 429 and any 5xx are worth repeating on the same model; a timeout is not (the model is slow, try another). */
    static boolean isRetryable(Exception e) {
        if (e instanceof RestClientResponseException r) {
            int status = r.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return false;
    }

    static boolean isTimeout(Exception e) {
        return e instanceof ResourceAccessException;   // timeout, connection reset, DNS
    }

    /** Short, secret-free status text for the log: "HTTP 503", "timeout/connection error", or the exception name. */
    static String describe(Exception e) {
        if (e instanceof RestClientResponseException r) return "HTTP " + r.getStatusCode().value();
        if (e instanceof ResourceAccessException) return "timeout/connection error";
        return e.getClass().getSimpleName();
    }

    private long nowMs() {
        return clock.getAsLong();
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** The built-in sample answer, clearly marked as not a real answer. */
    private String sampleAnswer(String prompt, String language) {
        boolean english = "english".equals(language);
        return (english ? BUSY_NOTE_ENGLISH : BUSY_NOTE_TAMIL) + "\n\n" + getDemoAnswer(prompt, language);
    }

    @SuppressWarnings("rawtypes")
    private String callGeminiApi(String model, String systemPrompt, List<ChatTurn> history, String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);   // key in a header, never in the URL

        String level = thinkingLevel == null ? "" : thinkingLevel.trim();
        String url = GEMINI_BASE + model + ":generateContent";
        try {
            Map<String, Object> body = buildGeminiBody(systemPrompt, history, userPrompt, temperature, maxOutputTokens, level);
            return extractGeminiText(restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class).getBody());
        } catch (HttpClientErrorException.BadRequest e) {
            if (level.isEmpty()) throw e;
            // The model may not accept this thinking level: ask once more without the thinking setting.
            log.warn("[AI] gemini {}: HTTP 400 with thinking level '{}', repeating without it", model, level);
            Map<String, Object> body = buildGeminiBody(systemPrompt, history, userPrompt, temperature, maxOutputTokens, null);
            return extractGeminiText(restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class).getBody());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String callGroqApi(String systemPrompt, List<ChatTurn> history, String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + groqApiKey);

        Map<String, Object> body = buildGroqBody(systemPrompt, history, userPrompt, temperature, maxOutputTokens);
        ResponseEntity<Map> response = restTemplate.postForEntity(GROQ_URL, new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                String result = msg == null ? null : (String) msg.get("content");
                log.info("[Groq] Response OK ({} chars)", result != null ? result.length() : 0);
                return result;
            }
        }
        throw new RuntimeException("Groq returned empty response");
    }

    // ── Request / response shapes (static so they can be unit-tested without the network) ──

    static Map<String, Object> buildGeminiBody(String systemPrompt, String userPrompt, double temperature, int maxTokens) {
        return buildGeminiBody(systemPrompt, userPrompt, temperature, maxTokens, null);
    }

    /** {@code thinkingLevel} null or blank: no thinking setting is sent. */
    static Map<String, Object> buildGeminiBody(String systemPrompt, String userPrompt, double temperature, int maxTokens,
                                               String thinkingLevel) {
        return buildGeminiBody(systemPrompt, List.of(), userPrompt, temperature, maxTokens, thinkingLevel);
    }

    /** Earlier turns go into {@code contents} before the new question; the assistant role is called "model" in Gemini. */
    static Map<String, Object> buildGeminiBody(String systemPrompt, List<ChatTurn> history, String userPrompt,
                                               double temperature, int maxTokens, String thinkingLevel) {
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", temperature);
        generationConfig.put("maxOutputTokens", maxTokens);
        if (thinkingLevel != null && !thinkingLevel.isBlank()) {
            generationConfig.put("thinkingConfig", Map.of("thinkingLevel", thinkingLevel.trim().toLowerCase()));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        List<Map<String, Object>> contents = new ArrayList<>();
        if (history != null) {
            for (ChatTurn t : history) {
                String role = ChatTurn.ASSISTANT.equals(t.role()) ? "model" : "user";
                contents.add(Map.of("role", role, "parts", List.of(Map.of("text", t.text()))));
            }
        }
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt))));
        body.put("contents", contents);
        body.put("generationConfig", generationConfig);
        return body;
    }

    static Map<String, Object> buildGroqBody(String systemPrompt, String userPrompt, double temperature, int maxTokens) {
        return buildGroqBody(systemPrompt, List.of(), userPrompt, temperature, maxTokens);
    }

    static Map<String, Object> buildGroqBody(String systemPrompt, List<ChatTurn> history, String userPrompt,
                                             double temperature, int maxTokens) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", GROQ_MODEL);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        if (history != null) {
            for (ChatTurn t : history) {
                messages.add(Map.of("role", ChatTurn.ASSISTANT.equals(t.role()) ? "assistant" : "user", "content", t.text()));
            }
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        return body;
    }

    /** Joins the answer parts and skips "thought" parts. Null when blocked or empty. */
    @SuppressWarnings("unchecked")
    static String extractGeminiText(Map<String, Object> responseBody) {
        if (responseBody == null) return null;
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
        if (candidates == null || candidates.isEmpty()) return null;
        Map<String, Object> candidate = candidates.get(0);
        String finishReason = (String) candidate.get("finishReason");
        if ("SAFETY".equals(finishReason) || "RECITATION".equals(finishReason)) return null;
        Map<String, Object> content = (Map<String, Object>) candidate.get("content");
        if (content == null) return null;
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null) return null;
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> part : parts) {
            if (Boolean.TRUE.equals(part.get("thought"))) continue;
            if (part.get("text") instanceof String t) sb.append(t);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Sample answer used by the voice path when the AI is unavailable. Same safe rules: no doses. */
    public String getVoiceDemoAnswer() {
        return "இப்போது AI பதில் கிடைக்கவில்லை. இதுவரை பொதுவாக செய்யக்கூடியவை:\n" +
               "1. வயலுக்குச் சென்று பயிரின் இலை, தண்டு, வேரை நேரில் பாருங்கள்.\n" +
               "2. தண்ணீர் தேங்காமல் வடிகால் சரியாக இருக்கிறதா என்று பாருங்கள்.\n" +
               "3. பாதிக்கப்பட்ட இலைகளை அகற்றுங்கள்.\n" +
               "4. எந்த மருந்து அல்லது உரம் போடுவதற்கு முன்பும், மருந்து டப்பாவில் உள்ள அளவைப் பின்பற்றி, " +
               "உங்கள் அருகிலுள்ள வேளாண் அலுவலகம் அல்லது கிருஷி விஞ்ஞான கேந்திரத்தில் உறுதி செய்யுங்கள்.";
    }

    /**
     * Sample answers used when no AI provider is available. They are general guidance only:
     * no pesticide or fertiliser doses, no scheme amounts, and they always point to the local expert.
     */
    public String getDemoAnswer(String prompt, String language) {
        if ("english".equals(language)) {
            return "The AI service is not available right now. In general: look at the crop in the field, check drainage, "
                    + "and remove badly affected leaves. Before using any pesticide or fertiliser, follow the product label "
                    + "and confirm with your local agriculture office or Krishi Vigyan Kendra.";
        }

        String lower = prompt == null ? "" : prompt.toLowerCase();
        String closing = "\n\nமருந்து அல்லது உரம் போடுவதற்கு முன் டப்பாவில் உள்ள அளவைப் பார்த்து, " +
                "அருகிலுள்ள வேளாண் அலுவலகத்தில் உறுதி செய்யுங்கள்.";

        if (lower.contains("நெல்") || lower.contains("rice") || lower.contains("paddy")) {
            return "நெல் சாகுபடி குறிப்புகள்:\n" +
                   "1. நல்ல தரமான, நோயற்ற விதையைத் தேர்ந்தெடுங்கள்.\n" +
                   "2. வயலில் தண்ணீர் தேங்காமல் பாசனத்தை சீராக வையுங்கள்.\n" +
                   "3. நடவு செய்த சில வாரங்களில் களை எடுங்கள்.\n" +
                   "4. கதிர்கள் தங்க நிறமானதும் அறுவடைக்குத் தயாராகுங்கள்." + closing;
        }

        if (lower.contains("தக்காளி") || lower.contains("tomato")) {
            return "தக்காளி சாகுபடி குறிப்புகள்:\n" +
                   "1. செடிகளுக்கு இடையே காற்றோட்டத்திற்கு போதிய இடைவெளி விடுங்கள்.\n" +
                   "2. வடிகால் வசதி உள்ள நிலத்தில் நடுங்கள்.\n" +
                   "3. நோய் தெரிந்த இலைகளை உடனே அகற்றுங்கள்.\n" +
                   "4. வாரம் ஒருமுறை செடிகளைக் கவனியுங்கள்." + closing;
        }

        if (lower.contains("வாழை") || lower.contains("banana")) {
            return "வாழை சாகுபடி குறிப்புகள்:\n" +
                   "1. நோயற்ற நல்ல கன்றுகளைத் தேர்ந்தெடுங்கள்.\n" +
                   "2. குழி தோண்டி மக்கிய தொழு உரம் கலந்து நடுங்கள்.\n" +
                   "3. மழைக்காலத்தில் வடிகால் வசதி செய்யுங்கள்.\n" +
                   "4. குலை வெளிப்பட்டதும் சூரிய வெப்பத்திலிருந்து காக்கவும்." + closing;
        }

        if (lower.contains("மண்") || lower.contains("soil") || lower.contains("fertilizer") || lower.contains("உரம்")) {
            return "மண் வள குறிப்புகள்:\n" +
                   "1. அருகிலுள்ள வேளாண் அலுவலகத்தில் மண் பரிசோதனை செய்யுங்கள்; எந்த சத்து குறைவு என்று தெரியும்.\n" +
                   "2. மக்கிய தொழு உரம் அல்லது கம்போஸ்ட் சேர்ப்பது மண்ணுக்கு நல்லது.\n" +
                   "3. பசுந்தாள் உரப்பயிர் விதைத்து மடக்கி உழுவதும் உதவும்.\n" +
                   "4. ரசாயன உரத்தின் அளவை மண் பரிசோதனை முடிவு மற்றும் வேளாண் அலுவலர் ஆலோசனைப்படி தீர்மானியுங்கள்.";
        }

        if (lower.contains("மஞ்சள்") || lower.contains("மஞ்சளா") ||
                (lower.contains("இலை") && (lower.contains("மாஞ்சு") || lower.contains("நிறம்")))) {
            return "இலை மஞ்சளாவதற்கு பொதுவான காரணங்கள்: தண்ணீர் தேங்குதல், சத்து குறைவு, பூச்சி அல்லது நோய்.\n" +
                   "1. வயலில் தண்ணீர் தேங்கியிருக்கிறதா என்று பாருங்கள்.\n" +
                   "2. மஞ்சள் எந்த இலையில், எங்கிருந்து தொடங்குகிறது என்று கவனியுங்கள்.\n" +
                   "3. இலையின் பின்புறம் பூச்சி இருக்கிறதா என்று பாருங்கள்." + closing;
        }

        if (lower.contains("பூச்சி") || lower.contains("insect") || lower.contains("pest") || lower.contains("கீட")) {
            return "பூச்சி கட்டுப்பாடு குறிப்புகள்:\n" +
                   "1. பாதிக்கப்பட்ட இலைகளையும் பூச்சிகளையும் அகற்றுங்கள்.\n" +
                   "2. மஞ்சள் ஒட்டும் பொறிகள் வைத்தால் பறக்கும் பூச்சிகள் குறையும்.\n" +
                   "3. எந்த பூச்சி என்று தெரியாவிட்டால் இலையின் படத்துடன் வேளாண் அலுவலரிடம் கேளுங்கள்." + closing;
        }

        if (lower.contains("தண்ணீர்") || lower.contains("water") || lower.contains("பாசனம்") || lower.contains("irrigation")) {
            return "பாசன குறிப்புகள்:\n" +
                   "1. சொட்டு நீர்ப்பாசனம் தண்ணீரை மிச்சப்படுத்தும்.\n" +
                   "2. காலை அல்லது மாலை நேரத்தில் பாசனம் செய்வது நல்லது.\n" +
                   "3. வயல் வரப்பில் மழை நீரைச் சேகரிக்கலாம்.\n" +
                   "4. மானியம் பற்றிய விவரங்களுக்கு வேளாண் அலுவலகத்தில் கேளுங்கள்.";
        }

        return "வணக்கம்! பயிர், பிரச்சனை, எந்த நிலையில் உள்ளது என்று சொல்லுங்கள்; என்னால் முடிந்த அளவு உதவுகிறேன்.";
    }
}
