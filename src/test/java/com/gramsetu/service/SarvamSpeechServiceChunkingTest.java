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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Long answers: split into parts, spoken in order, joined; one retry per part; all or nothing. No network. */
class SarvamSpeechServiceChunkingTest {

    private static final String URL = "https://api.sarvam.ai/text-to-speech";
    private static final String SECRET = "sk-test-SECRET-KEY-123";

    // Three sentences that fit a 30-character part on their own but not two together.
    private static final String S1 = "முதல் வாக்கியம் இது.";
    private static final String S2 = "இரண்டாம் வாக்கியம் இது.";
    private static final String S3 = "மூன்றாம் வாக்கியம் இது.";
    private static final String THREE = S1 + " " + S2 + " " + S3;

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

    private static String audio(String base64) {
        return "{\"request_id\":\"r\",\"audios\":[\"" + base64 + "\"]}";
    }

    private void expectPart(String text, String base64Audio) {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("api-subscription-key", SECRET))
                .andExpect(jsonPath("$.text").value(text))
                .andExpect(jsonPath("$.output_audio_codec").value("mp3"))
                .andRespond(withSuccess(audio(base64Audio), MediaType.APPLICATION_JSON));
    }

    private void expectFailure(String text, HttpStatus status) {
        server.expect(requestTo(URL)).andExpect(jsonPath("$.text").value(text)).andRespond(withStatus(status));
    }

    private void assertNoAnswerTextInLogs() {
        for (ILoggingEvent e : logs.list) {
            String line = e.getFormattedMessage();
            assertFalse(line.contains("வாக்கியம்"), "answer text leaked into a log line: " + line);
            assertFalse(line.contains(SECRET), "key leaked into a log line");
        }
    }

    @Test
    @DisplayName("The request asks for mp3 and carries the cleaned text (no markdown, no list number, no emoji)")
    void requestIsMp3AndClean() {
        expectPart("வணக்கம் நீர் பாய்ச்சவும்.", "QUJD");

        assertEquals("QUJD", service.toAudio("**வணக்கம்**\n1. நீர் ⚠️ பாய்ச்சவும்.", "KONGU"));
        server.verify();
    }

    @Test
    @DisplayName("A long answer: one request per part, in order, and the audio is the parts appended in that order")
    void multiPartOrderAndJoin() {
        service.chunkChars = 30;
        expectPart(S1, "AQ==");   // byte 0x01
        expectPart(S2, "Ag==");   // byte 0x02
        expectPart(S3, "Aw==");   // byte 0x03

        assertEquals("AQID", service.toAudio(THREE, "STANDARD"));   // 0x01 0x02 0x03
        server.verify();
        assertNoAnswerTextInLogs();
    }

    @Test
    @DisplayName("A part that fails once (503) is retried once and the audio is complete")
    void retryOnce() {
        service.chunkChars = 30;
        expectPart(S1, "AQ==");
        expectFailure(S2, HttpStatus.SERVICE_UNAVAILABLE);
        expectPart(S2, "Ag==");
        expectPart(S3, "Aw==");

        assertEquals("AQID", service.toAudio(THREE, "STANDARD"));
        server.verify();
        assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("retrying")));
        assertNoAnswerTextInLogs();
    }

    @Test
    @DisplayName("All or nothing: a part that fails twice gives no audio at all, and later parts are not requested")
    void allOrNothing() {
        service.chunkChars = 30;
        expectPart(S1, "AQ==");
        expectFailure(S2, HttpStatus.SERVICE_UNAVAILABLE);
        expectFailure(S2, HttpStatus.SERVICE_UNAVAILABLE);   // the retry; no request for S3 is expected

        assertEquals("", service.toAudio(THREE, "STANDARD"));
        server.verify();
        assertTrue(logs.list.stream().anyMatch(e -> e.getFormattedMessage().contains("continuing with the text reply")));
        assertNoAnswerTextInLogs();
    }

    @Test
    @DisplayName("A part returning no audio is retried once, then the whole result is empty")
    void emptyAudioRetriedThenNothing() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"audios\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"audios\":[]}", MediaType.APPLICATION_JSON));

        assertEquals("", service.toAudio(S1, "STANDARD"));
        server.verify();
    }

    @Test
    @DisplayName("A permanent client error (400) is not retried")
    void clientErrorNotRetried() {
        expectFailure(S1, HttpStatus.BAD_REQUEST);   // exactly one request is expected

        assertEquals("", service.toAudio(S1, "STANDARD"));
        server.verify();
        assertNoAnswerTextInLogs();
    }

    @Test
    @DisplayName("A part that is not valid base64 gives no audio instead of a broken clip")
    void invalidBase64() {
        service.chunkChars = 30;
        expectPart(S1, "AQ==");
        expectPart(S2, "***not base64***");

        assertEquals("", service.toAudio(S1 + " " + S2, "STANDARD"));
        server.verify();
    }

    @Test
    @DisplayName("An answer needing more than 6 parts is not spoken and Sarvam is not called")
    void tooManyParts() {
        service.chunkChars = 30;
        String seven = (S1 + " ").repeat(7).trim();   // 7 parts of 30 chars

        assertEquals("", service.toAudio(seven, "STANDARD"));
        server.verify();   // no expectations: any HTTP call would have failed the test
        assertTrue(logs.list.stream().anyMatch(e -> e.getFormattedMessage().contains("7 audio parts")));
    }

    @Test
    @DisplayName("Text that is only markdown or emoji sends nothing")
    void nothingToSpeak() {
        assertEquals("", service.toAudio("**  ** ⚠️ 🌿", "STANDARD"));
        assertEquals("", service.toAudio(null, "STANDARD"));
        server.verify();
    }

    @Test
    @DisplayName("toAudioLong is an alias of toAudio")
    void toAudioLongIsAlias() {
        expectPart(S1, "QUJD");
        assertEquals("QUJD", service.toAudioLong(S1, "STANDARD"));
        server.verify();
    }

    @Test
    @DisplayName("Parts are appended byte for byte; only transient failures are retryable")
    void joinAndRetryable() {
        assertArrayEquals(new byte[] {1, 2, 3, 4}, SarvamSpeechService.join(List.of(new byte[] {1, 2}, new byte[] {3}, new byte[] {4})));
        assertTrue(SarvamSpeechService.isRetryable(new RuntimeException("network")));
        assertTrue(SarvamSpeechService.isRetryable(org.springframework.web.client.HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, "x", org.springframework.http.HttpHeaders.EMPTY, new byte[0], null)));
        assertTrue(SarvamSpeechService.isRetryable(org.springframework.web.client.HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "x", org.springframework.http.HttpHeaders.EMPTY, new byte[0], null)));
        assertFalse(SarvamSpeechService.isRetryable(org.springframework.web.client.HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "x", org.springframework.http.HttpHeaders.EMPTY, new byte[0], null)));
    }
}
