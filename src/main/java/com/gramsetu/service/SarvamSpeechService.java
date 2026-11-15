package com.gramsetu.service;

import com.gramsetu.util.LogSafe;
import com.gramsetu.util.TtsText;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SarvamSpeechService {

    @Value("${sarvam.api.key}")
    private String apiKey;

    /** Text-to-speech model. bulbul:v2 was retired by Sarvam (HTTP 400); override with SARVAM_TTS_MODEL. */
    @Value("${sarvam.tts.model:bulbul:v3}")
    private String ttsModel = "bulbul:v3";

    /** Speech-to-text model. saarika:v2/v2.5 are legacy; override with SARVAM_STT_MODEL. */
    @Value("${sarvam.stt.model:saaras:v3}")
    private String sttModel = "saaras:v3";

    private final RestTemplate restTemplate;

    public SarvamSpeechService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
    }

    public String getTtsModel() { return ttsModel; }

    public String getSttModel() { return sttModel; }

    /** Sarvam language codes: Tamil, English (India), and "unknown" = detect the language from the audio. */
    static final String STT_TAMIL = "ta-IN";
    static final String STT_AUTO_DETECT = "unknown";
    static final String TTS_TAMIL = "ta-IN";
    static final String TTS_ENGLISH = "en-IN";

    public boolean isKeyConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && !apiKey.toUpperCase().startsWith("YOUR_");
    }

    /** Speech-to-text with the language fixed to Tamil (the default for the Tamil-first app). */
    public String toText(byte[] audioBytes, String mimeType) {
        return transcribe(audioBytes, mimeType, STT_TAMIL);
    }

    /** Speech-to-text that lets Sarvam detect the language (English mode on the website). */
    public String toTextAutoDetect(byte[] audioBytes, String mimeType) {
        return transcribe(audioBytes, mimeType, STT_AUTO_DETECT);
    }

    private String transcribe(byte[] audioBytes, String mimeType, String languageCode) {
        if (!isKeyConfigured()) {
            log.warn("Sarvam API key not configured – using demo transcript.");
            return "என் நெல் பயிரில் நோய் வந்துள்ளது. என்ன மருந்து போட வேண்டும்?";
        }

        // Retry up to 2 times before giving up
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String result = callSarvamSTT(audioBytes, mimeType, attempt, languageCode);
                if (result != null && !result.isBlank()
                        && !result.contains("குரல் புரியவில்லை")) {
                    log.info("[STT] Success on attempt {} ({} chars)", attempt, LogSafe.len(result));
                    return result;
                }
                log.warn("[STT] Attempt {} returned empty/error result", attempt);
            } catch (Exception e) {
                log.warn("[STT] Attempt {} failed (model={}): {}", attempt, sttModel, describeFailure(e));
            }
            if (attempt < 2) {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }

        log.error("[STT] All attempts failed for audio size={} bytes", audioBytes.length);
        return "குரல் புரியவில்லை. தெளிவாக பேசி மீண்டும் முயற்சிக்கவும்.";
    }

    @SuppressWarnings("unchecked")
    private String callSarvamSTT(byte[] audioBytes, String mimeType, int attempt, String languageCode) {
        String filename = (mimeType != null && mimeType.contains("ogg"))  ? "audio.ogg"
                : (mimeType != null && mimeType.contains("webm")) ? "audio.webm"
                : (mimeType != null && mimeType.contains("mp3"))  ? "audio.mp3"
                : "audio.wav";

        log.info("[STT] Attempt {} — size={} bytes, file={}", attempt, audioBytes.length, filename);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("api-subscription-key", apiKey);

        final String resolvedFilename = filename;
        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override public String getFilename() { return resolvedFilename; }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", audioResource);
        body.add("language_code", languageCode);
        body.add("model", sttModel);
        // On second attempt, add noise handling hint
        if (attempt == 2) {
            body.add("with_timestamps", "false");
        }

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://api.sarvam.ai/speech-to-text",
                requestEntity,
                Map.class
        );

        log.info("[STT] Response status: {}", response.getStatusCode());
        Map<String, Object> responseBody = response.getBody();   // holds the transcript: never logged

        if (responseBody != null && responseBody.containsKey("transcript")) {
            String transcript = (String) responseBody.get("transcript");
            if (transcript != null && !transcript.isBlank()) {
                return transcript;
            }
        }
        return null;
    }

    /** Largest text sent in one request. bulbul:v3 accepts 2500 characters; this leaves a wide margin. */
    static final int MAX_CHUNK_CHARS = 1200;

    /** An answer needing more audio parts than this is not spoken at all (text reply only), never half-spoken. */
    static final int MAX_TTS_CHUNKS = 6;

    /** Attempts per audio part: the first try and one retry. */
    private static final int TTS_ATTEMPTS = 2;

    // replaceable in tests
    int chunkChars = MAX_CHUNK_CHARS;
    long retryBackoffMs = 500;

    /**
     * Converts Tamil text to speech (base64 mp3) with a dialect-matched voice (see {@link SarvamTtsVoices}).
     *
     * The answer is cleaned of markdown, split into sentence-sized parts under the provider limit, spoken part by part
     * and the parts are joined in order. All or nothing: each part gets one retry, and if a part still fails the result
     * is "" (the caller sends the text reply alone) rather than audio that stops partway.
     * Never throws and never logs the text.
     */
    public String toAudio(String tamilText, String dialect) {
        String speaker = SarvamTtsVoices.speakerFor(dialect);
        if (!SarvamTtsVoices.isValidV3(speaker) && ttsModel.startsWith("bulbul:v3")) {
            speaker = SarvamTtsVoices.DEFAULT;
        }
        return speak(tamilText, speaker, TTS_TAMIL);
    }

    /**
     * Converts an English answer to speech (language code en-IN) with Sarvam's default voice. Same rules as
     * {@link #toAudio}: all or nothing, "" when it cannot be done, never throws, never logs the text.
     */
    public String toEnglishAudio(String englishText) {
        return speak(englishText, SarvamTtsVoices.DEFAULT, TTS_ENGLISH);
    }

    private String speak(String text, String speaker, String languageCode) {
        if (!isKeyConfigured()) {
            log.warn("[TTS] Sarvam API key not configured, skipping audio");
            return "";
        }
        List<String> chunks = TtsText.chunks(text, chunkChars);
        if (chunks.isEmpty()) return "";
        if (chunks.size() > MAX_TTS_CHUNKS) {
            log.warn("[TTS] The answer needs {} audio parts (limit {}); continuing with the text reply", chunks.size(), MAX_TTS_CHUNKS);
            return "";
        }
        log.debug("[TTS] model={} speaker={} language={} parts={}", ttsModel, speaker, languageCode, chunks.size());

        List<byte[]> parts = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String audio = synthesizeWithRetry(chunks.get(i), speaker, languageCode, i + 1, chunks.size());
            if (audio.isEmpty()) return "";                 // already logged; all or nothing
            if (chunks.size() == 1) return audio;
            try {
                parts.add(Base64.getDecoder().decode(audio));
            } catch (IllegalArgumentException e) {
                log.warn("[TTS] Sarvam returned audio part {}/{} that is not valid base64; continuing with the text reply",
                        i + 1, chunks.size());
                return "";
            }
        }
        return Base64.getEncoder().encodeToString(join(parts));
    }

    /** One part, with one retry on a transient failure. Returns "" when the part could not be spoken. */
    private String synthesizeWithRetry(String text, String speaker, String languageCode, int part, int total) {
        for (int attempt = 1; attempt <= TTS_ATTEMPTS; attempt++) {
            boolean retryable = true;
            String problem;
            try {
                String audio = callTts(text, speaker, languageCode);
                if (!audio.isEmpty()) return audio;
                problem = "returned no audio";
            } catch (Exception e) {
                problem = "failed: " + describeFailure(e);
                retryable = isRetryable(e);
            }
            boolean willRetry = retryable && attempt < TTS_ATTEMPTS;
            // One short line, no stack trace, no text. The API key is only ever sent as a header.
            log.warn("[TTS] Sarvam TTS part {}/{} attempt {}/{} {} (model={}, speaker={}); {}",
                    part, total, attempt, TTS_ATTEMPTS, problem, ttsModel, speaker,
                    willRetry ? "retrying" : "continuing with the text reply");
            if (!willRetry) return "";
            sleepQuietly(retryBackoffMs);
        }
        return "";
    }

    private String callTts(String text, String speaker, String languageCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-subscription-key", apiKey);

        Map<String, Object> body = buildTtsBody(text, ttsModel, speaker, languageCode);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://api.sarvam.ai/text-to-speech", new HttpEntity<>(body, headers), Map.class);
        return extractAudio(response.getBody());
    }

    /** Transient failures are worth one more try: network errors, 429 and 5xx. Other 4xx errors will not improve. */
    static boolean isRetryable(Exception e) {
        if (e instanceof RestClientResponseException r) {
            int status = r.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return true;
    }

    /** The mp3 parts appended in order: concatenated mp3 frames play as one clip. */
    static byte[] join(List<byte[]> parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }

    private static void sleepQuietly(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** Convenience overload: neutral voice when the dialect is unknown. */
    public String toAudio(String tamilText) {
        return toAudio(tamilText, "STANDARD");
    }

    /** bulbul:v3 request: {@code text} (up to 2500 chars), {@code language_code}, and mp3 output so parts can be appended. */
    static Map<String, Object> buildTtsBody(String text, String model, String speaker) {
        return buildTtsBody(text, model, speaker, TTS_TAMIL);
    }

    static Map<String, Object> buildTtsBody(String text, String model, String speaker, String languageCode) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("text", text);
        body.put("language_code", languageCode);
        body.put("model", model);
        body.put("speaker", speaker);
        body.put("output_audio_codec", "mp3");
        return body;
    }

    /** First audio string of the response ({@code audios[0]}, base64 mp3), or "" when there is none. */
    static String extractAudio(Map<String, Object> responseBody) {
        if (responseBody != null && responseBody.get("audios") instanceof List<?> audios && !audios.isEmpty()
                && audios.get(0) instanceof String audio) {
            return audio;
        }
        return "";
    }

    /** A short description of a failed call for the log: the exception class and HTTP status only, never the error body. */
    static String describeFailure(Exception e) {
        return LogSafe.error(e);
    }

    /** Same as {@link #toAudio}: long answers are now split into parts there. Kept for existing callers. */
    public String toAudioLong(String tamilText, String dialect) {
        return toAudio(tamilText, dialect);
    }
}
