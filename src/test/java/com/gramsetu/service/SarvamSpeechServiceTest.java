package com.gramsetu.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Sarvam calls against a mocked HTTP server: no network, no real key. */
class SarvamSpeechServiceTest {

    private static final String SECRET = "sk-test-SECRET-KEY-123";

    private SarvamSpeechService service;
    private MockRestServiceServer server;
    private ListAppender<ILoggingEvent> logs;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        service = new SarvamSpeechService();
        ReflectionTestUtils.setField(service, "apiKey", SECRET);
        RestTemplate rt = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        server = MockRestServiceServer.bindTo(rt).build();

        serviceLogger = (Logger) LoggerFactory.getLogger(SarvamSpeechService.class);
        logs = new ListAppender<>();
        logs.start();
        serviceLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logs);
    }

    @Test
    @DisplayName("Defaults are the current models, not the retired ones")
    void defaultModels() {
        assertEquals("bulbul:v3", service.getTtsModel());
        assertEquals("saaras:v3", service.getSttModel());
    }

    @Test
    @DisplayName("TTS sends the bulbul:v3 request (text, language_code, model, a valid v3 speaker) and returns the audio")
    void ttsSuccess() {
        server.expect(requestTo("https://api.sarvam.ai/text-to-speech"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-subscription-key", SECRET))
                .andExpect(jsonPath("$.text").value("வணக்கம்"))
                .andExpect(jsonPath("$.language_code").value("ta-IN"))
                .andExpect(jsonPath("$.model").value("bulbul:v3"))
                .andExpect(jsonPath("$.speaker").value("vijay"))          // KONGU
                .andExpect(jsonPath("$.inputs").doesNotExist())            // v2-style field is gone
                .andExpect(jsonPath("$.target_language_code").doesNotExist())
                .andRespond(withSuccess("{\"request_id\":\"r1\",\"audios\":[\"QUJD\"]}", MediaType.APPLICATION_JSON));

        assertEquals("QUJD", service.toAudio("வணக்கம்", "KONGU"));
        server.verify();
    }

    @Test
    @DisplayName("TTS model can be changed with SARVAM_TTS_MODEL")
    void ttsModelIsConfigurable() {
        ReflectionTestUtils.setField(service, "ttsModel", "bulbul:v9-test");
        server.expect(requestTo("https://api.sarvam.ai/text-to-speech"))
                .andExpect(jsonPath("$.model").value("bulbul:v9-test"))
                .andRespond(withSuccess("{\"audios\":[\"QUJD\"]}", MediaType.APPLICATION_JSON));

        assertEquals("QUJD", service.toAudio("test", "STANDARD"));
        server.verify();
    }

    @Test
    @DisplayName("TTS 400 'model deprecated': returns empty audio (text reply still works) and logs ONE clear warning, no stack trace, no key")
    void ttsDeprecatedModelFailsGracefully() {
        server.expect(requestTo("https://api.sarvam.ai/text-to-speech"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Model 'bulbul:v2' has been deprecated. Please use 'bulbul:v3' instead.\"}}"));

        String audio = service.toAudio("வணக்கம்", "KONGU");

        assertEquals("", audio);
        List<ILoggingEvent> warnings = logs.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
        assertEquals(1, warnings.size(), "exactly one warning line");
        ILoggingEvent w = warnings.get(0);
        assertNull(w.getThrowableProxy(), "no stack trace");
        String line = w.getFormattedMessage();
        assertTrue(line.contains("HTTP 400"), line);
        assertFalse(line.contains("deprecated"), "the API error body must not be logged: " + line);
        assertTrue(line.contains("continuing with the text reply"), line);
        assertFalse(line.contains(SECRET), "the API key must never be logged");
        assertFalse(line.contains("\n"), "a single line");
        assertTrue(logs.list.stream().noneMatch(e -> e.getLevel() == Level.ERROR), "no ERROR with a stack trace");
    }

    @Test
    @DisplayName("TTS response without audio (also after the one retry) gives an empty string and a warning")
    void ttsEmptyAudio() {
        service.retryBackoffMs = 0;
        server.expect(requestTo("https://api.sarvam.ai/text-to-speech"))
                .andRespond(withSuccess("{\"audios\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.sarvam.ai/text-to-speech"))
                .andRespond(withSuccess("{\"audios\":[]}", MediaType.APPLICATION_JSON));
        assertEquals("", service.toAudio("test", "STANDARD"));
        assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.WARN));
    }

    @Test
    @DisplayName("STT sends the configured model (saaras:v3) and ta-IN, and returns the transcript")
    void sttUsesConfiguredModel() {
        server.expect(requestTo("https://api.sarvam.ai/speech-to-text"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-subscription-key", SECRET))
                .andExpect(content().string(containsString("saaras:v3")))
                .andExpect(content().string(containsString("ta-IN")))
                .andRespond(withSuccess("{\"transcript\":\"நெல் பயிர்\",\"language_code\":\"ta-IN\"}", MediaType.APPLICATION_JSON));

        assertEquals("நெல் பயிர்", service.toText(new byte[]{1, 2, 3}, "audio/wav"));
        server.verify();
    }

    @Test
    @DisplayName("STT never logs the transcript, at any level: only the attempt number and the length")
    void sttDoesNotLogTheTranscript() {
        serviceLogger.setLevel(Level.DEBUG);
        server.expect(requestTo("https://api.sarvam.ai/speech-to-text"))
                .andRespond(withSuccess("{\"transcript\":\"நெல் பயிர்\"}", MediaType.APPLICATION_JSON));

        service.toText(new byte[]{1, 2, 3}, "audio/wav");

        assertTrue(logs.list.stream().noneMatch(e -> e.getFormattedMessage().contains("நெல் பயிர்")),
                "the transcript leaked into a log line");
        assertTrue(logs.list.stream().anyMatch(e -> e.getFormattedMessage().contains("Success on attempt 1 (")),
                "the success line (attempt and length) should still be logged");
    }

    @Test
    @DisplayName("Dialect-to-speaker map only uses names that bulbul:v3 accepts; the old v2 names are not valid")
    void speakersAreValidForV3() {
        for (String dialect : List.of("KONGU", "MADURAI", "NELLAI", "CHENNAI", "MADRAS", "STANDARD", "unknown", "")) {
            String speaker = SarvamTtsVoices.speakerFor(dialect);
            assertTrue(SarvamTtsVoices.isValidV3(speaker), dialect + " -> " + speaker);
        }
        assertTrue(SarvamTtsVoices.isValidV3(SarvamTtsVoices.speakerFor(null)));
        for (String v2Only : List.of("abhilash", "anushka", "manisha", "vidya", "arya", "karun")) {
            assertFalse(SarvamTtsVoices.isValidV3(v2Only), v2Only + " is a v2 speaker");
        }
    }

    @Test
    @DisplayName("describeFailure gives the exception class only, never its message")
    void describeFailureIsShort() {
        String d = SarvamSpeechService.describeFailure(new RuntimeException("a\nb\n" + "x".repeat(500)));
        assertEquals("RuntimeException", d);
    }
}
