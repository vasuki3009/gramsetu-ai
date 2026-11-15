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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** English speech: the English voice for answers and language auto-detection for questions. No network. */
class SarvamSpeechServiceLanguageTest {

    private static final String TTS = "https://api.sarvam.ai/text-to-speech";
    private static final String STT = "https://api.sarvam.ai/speech-to-text";
    private static final String SECRET = "sk-test-SECRET-KEY-123";
    private static final String ENGLISH = "Remove the infected leaves and keep the field clean.";

    private SarvamSpeechService service;
    private MockRestServiceServer server;
    private ListAppender<ILoggingEvent> logs;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        service = new SarvamSpeechService();
        ReflectionTestUtils.setField(service, "apiKey", SECRET);
        service.retryBackoffMs = 0;
        RestTemplate rt = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        server = MockRestServiceServer.bindTo(rt).build();

        serviceLogger = (Logger) LoggerFactory.getLogger(SarvamSpeechService.class);
        serviceLogger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        serviceLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logs);
        serviceLogger.setLevel(null);
    }

    @Test
    @DisplayName("English audio: language_code en-IN, the default voice, mp3, the cleaned text")
    void englishAudioRequest() {
        server.expect(requestTo(TTS)).andExpect(method(HttpMethod.POST))
                .andExpect(header("api-subscription-key", SECRET))
                .andExpect(jsonPath("$.language_code").value("en-IN"))
                .andExpect(jsonPath("$.speaker").value("shubh"))
                .andExpect(jsonPath("$.model").value("bulbul:v3"))
                .andExpect(jsonPath("$.output_audio_codec").value("mp3"))
                .andExpect(jsonPath("$.text").value("Remove the infected leaves and keep the field clean."))
                .andRespond(withSuccess("{\"audios\":[\"QUJD\"]}", MediaType.APPLICATION_JSON));

        assertEquals("QUJD", service.toEnglishAudio("**" + ENGLISH + "**"));   // markdown is stripped as for Tamil
        server.verify();
    }

    @Test
    @DisplayName("Tamil audio is unchanged: ta-IN and the dialect voice")
    void tamilAudioStillTamil() {
        server.expect(requestTo(TTS))
                .andExpect(jsonPath("$.language_code").value("ta-IN"))
                .andExpect(jsonPath("$.speaker").value("vijay"))   // KONGU
                .andRespond(withSuccess("{\"audios\":[\"QUJD\"]}", MediaType.APPLICATION_JSON));

        assertEquals("QUJD", service.toAudio("வணக்கம்", "KONGU"));
        server.verify();
    }

    @Test
    @DisplayName("A long English answer is spoken in parts, in order, all in en-IN")
    void longEnglishAnswerInParts() {
        service.chunkChars = 40;
        String a = "First sentence is here for you.";
        String b = "Second sentence is here for you.";
        for (String text : new String[] {a, b}) {
            server.expect(requestTo(TTS)).andExpect(jsonPath("$.language_code").value("en-IN"))
                    .andExpect(jsonPath("$.text").value(text))
                    .andRespond(withSuccess("{\"audios\":[\"" + (text.equals(a) ? "AQ==" : "Ag==") + "\"]}", MediaType.APPLICATION_JSON));
        }

        assertEquals("AQI=", service.toEnglishAudio(a + " " + b));   // bytes 0x01 0x02
        server.verify();
    }

    @Test
    @DisplayName("If English speech fails (after one retry) the result is empty, so the caller keeps the text")
    void englishAudioFailureIsEmpty() {
        server.expect(requestTo(TTS)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(TTS)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertEquals("", service.toEnglishAudio(ENGLISH));
        server.verify();
        for (ILoggingEvent e : logs.list) {
            assertFalse(e.getFormattedMessage().contains("infected leaves"), "answer text leaked into a log line");
        }
    }

    @Test
    @DisplayName("English mode listens with language auto-detection (unknown)")
    void sttAutoDetect() {
        server.expect(requestTo(STT)).andExpect(method(HttpMethod.POST))
                .andExpect(header("api-subscription-key", SECRET))
                .andExpect(content().string(containsString("unknown")))
                .andExpect(content().string(containsString("saaras:v3")))
                .andRespond(withSuccess("{\"transcript\":\"How do I join the PM Kisan scheme\"}", MediaType.APPLICATION_JSON));

        assertEquals("How do I join the PM Kisan scheme", service.toTextAutoDetect(new byte[] {1, 2, 3}, "audio/webm"));
        server.verify();
    }

    @Test
    @DisplayName("Tamil mode keeps ta-IN for speech-to-text")
    void sttTamilUnchanged() {
        server.expect(requestTo(STT))
                .andExpect(content().string(containsString("ta-IN")))
                .andRespond(withSuccess("{\"transcript\":\"நெல் பயிர்\"}", MediaType.APPLICATION_JSON));

        assertEquals("நெல் பயிர்", service.toText(new byte[] {1, 2, 3}, "audio/webm"));
        server.verify();
    }

    @Test
    @DisplayName("The transcript is never logged, in auto-detect mode either")
    void sttAutoDetectDoesNotLogTheTranscript() {
        server.expect(requestTo(STT))
                .andRespond(withSuccess("{\"transcript\":\"my private english question\"}", MediaType.APPLICATION_JSON));

        service.toTextAutoDetect(new byte[] {1}, "audio/webm");

        for (ILoggingEvent e : logs.list) {
            assertFalse(e.getFormattedMessage().contains("private english question"), e.getFormattedMessage());
        }
    }
}
