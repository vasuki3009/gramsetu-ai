package com.gramsetu.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Photo + prompt goes through the same chain as text: fallback models, retries, thinking level, output cap. */
class AIAdvisorServiceVisionTest {

    private static final String SECRET = "AIza-test-SECRET-KEY";
    private static final String IMAGE_B64 = "SGVsbG9JbWFnZURhdGE=";
    private static final String ANSWER_TEXT = "Crop: Tomato SECRET-ANSWER";
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String PRIMARY = BASE + "gemini-3.8-flash:generateContent";
    private static final String FALLBACK_1 = BASE + "gemini-3.6-flash:generateContent";
    private static final String FALLBACK_2 = BASE + "gemini-3.5-flash-lite:generateContent";
    private static final String BUSY_503 = "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}";

    private AIAdvisorService service;
    private MockRestServiceServer server;
    private final List<Long> sleeps = new ArrayList<>();
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        service = new AIAdvisorService();
        ReflectionTestUtils.setField(service, "apiKey", SECRET);
        ReflectionTestUtils.setField(service, "groqApiKey", "gsk-should-never-be-used");
        service.sleeper = sleeps::add;
        server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate")).build();

        logger = (Logger) LoggerFactory.getLogger(AIAdvisorService.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private static String geminiOk(String text) {
        return "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"" + text + "\"}]}}]}";
    }

    private Optional<String> run() {
        return service.generateFromImage("diagnose this", "image/jpeg", IMAGE_B64);
    }

    @Test
    void requestCarriesImageThinkingLevelAndOutputCap() {
        server.expect(once(), requestTo(PRIMARY))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"inline_data\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(IMAGE_B64)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"thinkingLevel\":\"low\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"maxOutputTokens\":2048")))
                .andRespond(withSuccess(geminiOk("ok"), MediaType.APPLICATION_JSON));

        assertEquals(Optional.of("ok"), run());
        server.verify();
    }

    @Test
    void timeoutMovesToFallbackModel_noRetryOnSlowModel() {
        server.expect(once(), requestTo(PRIMARY)).andRespond(withException(new IOException("Read timed out")));
        server.expect(once(), requestTo(FALLBACK_1)).andRespond(withSuccess(geminiOk("ok"), MediaType.APPLICATION_JSON));

        assertEquals(Optional.of("ok"), run());
        assertTrue(sleeps.isEmpty());
        server.verify();
    }

    @Test
    void retries503OnTheSameModel() {
        server.expect(once(), requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON).body(BUSY_503));
        server.expect(once(), requestTo(PRIMARY)).andRespond(withSuccess(geminiOk("ok"), MediaType.APPLICATION_JSON));

        assertEquals(Optional.of("ok"), run());
        assertEquals(List.of(1000L), sleeps);
        server.verify();
    }

    @Test
    void allModelsFail_returnsEmpty_andNeverCallsGroq() {
        server.expect(once(), requestTo(PRIMARY)).andRespond(withException(new IOException("Read timed out")));
        server.expect(once(), requestTo(FALLBACK_1)).andRespond(withException(new IOException("Read timed out")));
        server.expect(once(), requestTo(FALLBACK_2)).andRespond(withException(new IOException("Read timed out")));

        assertEquals(Optional.empty(), run());   // a Groq call would hit an unexpected URL and fail the verify
        server.verify();
    }

    @Test
    void noKey_noCall() {
        ReflectionTestUtils.setField(service, "apiKey", "");
        assertEquals(Optional.empty(), run());
        server.verify();
    }

    @Test
    void logsNeverContainTheKeyTheImageOrTheAnswer() {
        server.expect(once(), requestTo(PRIMARY)).andRespond(withSuccess(geminiOk(ANSWER_TEXT), MediaType.APPLICATION_JSON));
        assertTrue(run().isPresent());
        logs.list.forEach(e -> {
            String m = e.getFormattedMessage();
            assertFalse(m.contains(SECRET));
            assertFalse(m.contains(IMAGE_B64));
            assertFalse(m.contains("SECRET-ANSWER"));
        });
    }

    @Test
    void bodyShape_imagePartIsInlineData() {
        Map<String, Object> body = AIAdvisorService.buildGeminiVisionBody("p", "image/png", IMAGE_B64, 0.2, 2048, "low");
        String s = body.toString();
        assertTrue(s.contains("mime_type=image/png"), s);
        assertTrue(s.contains("thinkingLevel=low"), s);
    }
}
