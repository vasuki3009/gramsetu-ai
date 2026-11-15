package com.gramsetu.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Retry (defaults: 2 attempts per model), model fallback and the "AI is busy" sample answer, against a mocked HTTP server (no network, no sleeping). */
class AIAdvisorServiceRetryTest {

    private static final String SECRET = "AIza-test-SECRET-KEY";
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String PRIMARY = BASE + "gemini-3.8-flash:generateContent";
    private static final String FALLBACK_1 = BASE + "gemini-3.6-flash:generateContent";
    private static final String FALLBACK_2 = BASE + "gemini-3.5-flash-lite:generateContent";
    private static final String GROQ = "https://api.groq.com/openai/v1/chat/completions";

    private static final String BUSY_503 =
            "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\",\"message\":\"This model is currently experiencing high demand.\"}}";

    private AIAdvisorService service;
    private MockRestServiceServer server;
    private final List<Long> sleeps = new ArrayList<>();
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        service = new AIAdvisorService();
        ReflectionTestUtils.setField(service, "apiKey", SECRET);
        ReflectionTestUtils.setField(service, "groqApiKey", "");
        service.sleeper = sleeps::add;   // record the backoff instead of sleeping
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

    private void respond503(String url, int count) {
        server.expect(times(count), requestTo(url))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON).body(BUSY_503));
    }

    private List<String> attemptLines() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).filter(m -> m.startsWith("[AI]")).toList();
    }

    @Test
    @DisplayName("503 then 200: the same model is retried after a 1 s backoff and the real answer is returned")
    void retriesAfter503() {
        server.expect(once(), requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON).body(BUSY_503));
        server.expect(once(), requestTo(PRIMARY)).andRespond(withSuccess(geminiOk("1. Check drainage."), MediaType.APPLICATION_JSON));

        String answer = service.getAnswer("question", "tamil", "KONGU");

        assertEquals("1. Check drainage.", answer);
        assertFalse(answer.contains(AIAdvisorService.BUSY_NOTE_TAMIL));
        assertEquals(List.of(1000L), sleeps);
        server.verify();

        // one short line per attempt: model and status, never the key
        List<String> lines = attemptLines();
        assertEquals(2, lines.size(), lines.toString());
        assertTrue(lines.get(0).contains("gemini gemini-3.8-flash attempt 1/2: HTTP 503"), lines.get(0));
        assertTrue(lines.get(1).contains("gemini gemini-3.8-flash attempt 2/2: 200 OK"), lines.get(1));
        logs.list.forEach(e -> {
            assertFalse(e.getFormattedMessage().contains(SECRET));
            assertNull(e.getThrowableProxy(), "no stack traces");
        });
    }

    @Test
    @DisplayName("A timeout is NOT retried on the same slow model: the next model is tried right away")
    void timeoutMovesToNextModel() {
        server.expect(once(), requestTo(PRIMARY)).andRespond(withException(new IOException("Read timed out")));
        server.expect(once(), requestTo(FALLBACK_1)).andRespond(withSuccess(geminiOk("ok"), MediaType.APPLICATION_JSON));

        assertEquals("ok", service.getAnswer("q", "tamil", "STANDARD"));
        assertTrue(sleeps.isEmpty(), "no backoff after a timeout");
        assertTrue(attemptLines().get(0).contains("timeout/connection error, trying the next model"), attemptLines().get(0));
        server.verify();
    }

    @Test
    @DisplayName("503, then timeouts: the budget running out never stops the fallback models from being tried")
    void everyModelGetsAnAttemptEvenWhenBudgetIsUsedUp() {
        // the exact Render log: 503 first, then timeouts, and 20 s are gone
        long[] now = {0};
        service.clock = () -> now[0];
        service.sleeper = ms -> now[0] += 100_000;   // any sleep burns the whole 60 s budget
        respond503(PRIMARY, 2);   // 503, a 1 s backoff that burns the budget, 503 again
        server.expect(once(), requestTo(FALLBACK_1)).andRespond(withException(new IOException("Read timed out")));
        server.expect(once(), requestTo(FALLBACK_2)).andRespond(withSuccess(geminiOk("last model"), MediaType.APPLICATION_JSON));

        assertEquals("last model", service.getAnswer("q", "tamil", "STANDARD"));
        server.verify();
    }

    @Test
    @DisplayName("429 is retried with backoff 1 s then 2 s; the third attempt can still succeed")
    void backoffDoubles() {
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        server.expect(times(2), requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(once(), requestTo(PRIMARY)).andRespond(withSuccess(geminiOk("third time"), MediaType.APPLICATION_JSON));

        assertEquals("third time", service.getAnswer("q", "tamil", "STANDARD"));
        assertEquals(List.of(1000L, 2000L), sleeps);
        server.verify();
    }

    @Test
    @DisplayName("Primary keeps failing: after 2 attempts the next model answers")
    void fallsBackToNextModel() {
        respond503(PRIMARY, 2);
        server.expect(once(), requestTo(FALLBACK_1)).andRespond(withSuccess(geminiOk("from fallback"), MediaType.APPLICATION_JSON));

        assertEquals("from fallback", service.getAnswer("q", "tamil", "STANDARD"));
        assertEquals(List.of(1000L), sleeps);   // no sleep after the last attempt on a model
        server.verify();
    }

    @Test
    @DisplayName("All Gemini models fail: Groq answers when its key is set")
    void fallsBackToGroq() {
        ReflectionTestUtils.setField(service, "groqApiKey", "gsk-test");
        respond503(PRIMARY, 2);
        respond503(FALLBACK_1, 2);
        respond503(FALLBACK_2, 2);
        server.expect(once(), requestTo(GROQ)).andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"content\":\"from groq\"}}]}", MediaType.APPLICATION_JSON));

        assertEquals("from groq", service.getAnswer("q", "tamil", "STANDARD"));
        server.verify();
    }

    @Test
    @DisplayName("503 on every model (no Groq key): the sample answer, with the Tamil 'AI is busy' note first")
    void allModelsBusyGivesMarkedSampleAnswer() {
        respond503(PRIMARY, 2);
        respond503(FALLBACK_1, 2);
        respond503(FALLBACK_2, 2);

        String answer = service.getAnswer("நெல் பூச்சி", "tamil", "STANDARD");

        assertTrue(answer.startsWith("இப்போது AI பிஸியாக உள்ளது, சிறிது நேரம் கழித்து மீண்டும் கேளுங்கள்"), answer);
        assertTrue(answer.length() > AIAdvisorService.BUSY_NOTE_TAMIL.length() + 20, "the sample answer follows the note");
        assertEquals(List.of(1000L, 1000L, 1000L), sleeps);   // one 1 s backoff on each of the 3 models
        assertEquals(6, attemptLines().size());
        server.verify();
    }

    @Test
    @DisplayName("English questions get the English 'busy' note")
    void englishBusyNote() {
        ReflectionTestUtils.setField(service, "fallbackModels", "");
        respond503(PRIMARY, 2);

        String answer = service.getAnswer("my rice leaves are yellow", "english", "STANDARD");

        assertTrue(answer.startsWith(AIAdvisorService.BUSY_NOTE_ENGLISH), answer);
        server.verify();
    }

    @Test
    @DisplayName("400 is not retried on the same model (one call, no sleeping)")
    void noRetryOn400() {
        ReflectionTestUtils.setField(service, "fallbackModels", "");   // one model only, so the call count is exact
        ReflectionTestUtils.setField(service, "thinkingLevel", "");    // no thinking setting, so no second request without it
        server.expect(once(), requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"message\":\"API key not valid\"}}"));

        String answer = service.getAnswer("q", "tamil", "STANDARD");

        assertTrue(answer.startsWith(AIAdvisorService.BUSY_NOTE_TAMIL));
        assertTrue(sleeps.isEmpty(), "no backoff for a 400");
        assertEquals(1, attemptLines().size());
        assertTrue(attemptLines().get(0).contains("HTTP 400") && attemptLines().get(0).contains("not retrying"));
        server.verify();   // exactly one request; a second one would fail the mock
    }

    @Test
    @DisplayName("400 on the primary still lets the next model try once")
    void fourHundredMovesToNextModel() {
        ReflectionTestUtils.setField(service, "thinkingLevel", "");
        server.expect(once(), requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(once(), requestTo(FALLBACK_1)).andRespond(withSuccess(geminiOk("next model"), MediaType.APPLICATION_JSON));

        assertEquals("next model", service.getAnswer("q", "tamil", "STANDARD"));
        assertTrue(sleeps.isEmpty());
        server.verify();
    }

    @Test
    @DisplayName("Budget already used up: no retries, but every model still gets exactly one attempt")
    void budgetStopsRetriesNotModels() {
        ReflectionTestUtils.setField(service, "budgetMs", 0L);
        respond503(PRIMARY, 1);
        respond503(FALLBACK_1, 1);
        respond503(FALLBACK_2, 1);

        String answer = service.getAnswer("q", "tamil", "STANDARD");

        assertTrue(answer.startsWith(AIAdvisorService.BUSY_NOTE_TAMIL));
        assertTrue(sleeps.isEmpty());
        server.verify();   // one request per model, no more
    }

    @Test
    @DisplayName("Timeouts on every model: one attempt each, no sleeping, then the marked sample answer")
    void allModelsTimeOut() {
        for (String url : List.of(PRIMARY, FALLBACK_1, FALLBACK_2)) {
            server.expect(once(), requestTo(url)).andRespond(withException(new IOException("Read timed out")));
        }

        String answer = service.getAnswer("q", "tamil", "STANDARD");

        assertTrue(answer.startsWith(AIAdvisorService.BUSY_NOTE_TAMIL));
        assertTrue(sleeps.isEmpty());
        assertEquals(3, attemptLines().size());
        server.verify();
    }

    @Test
    @DisplayName("The request carries thinkingConfig.thinkingLevel (default low)")
    void sendsThinkingLevel() {
        server.expect(once(), requestTo(PRIMARY))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"thinkingConfig\":{\"thinkingLevel\":\"low\"}")))
                .andRespond(withSuccess(geminiOk("ok"), MediaType.APPLICATION_JSON));

        assertEquals("ok", service.getAnswer("q", "tamil", "STANDARD"));
        server.verify();
    }

    @Test
    @DisplayName("AI_THINKING_LEVEL empty: no thinking setting is sent")
    void noThinkingConfigWhenEmpty() {
        ReflectionTestUtils.setField(service, "thinkingLevel", "");
        server.expect(once(), requestTo(PRIMARY))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("thinkingConfig"))))
                .andRespond(withSuccess(geminiOk("ok"), MediaType.APPLICATION_JSON));

        assertEquals("ok", service.getAnswer("q", "tamil", "STANDARD"));
        server.verify();
    }

    @Test
    @DisplayName("A model that rejects the thinking level (400) is asked once more without it")
    void thinkingLevelRejectedIsDroppedOnce() {
        server.expect(once(), requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"message\":\"thinking level not supported\"}}"));
        server.expect(once(), requestTo(PRIMARY))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("thinkingConfig"))))
                .andRespond(withSuccess(geminiOk("without thinking"), MediaType.APPLICATION_JSON));

        assertEquals("without thinking", service.getAnswer("q", "tamil", "STANDARD"));
        assertTrue(sleeps.isEmpty());
        server.verify();
    }

    @Test
    @DisplayName("Defaults: 2 attempts per model, 60 s budget, 25 s read timeout, thinking level low")
    void defaults() {
        assertEquals(2, ReflectionTestUtils.getField(service, "maxAttempts"));
        assertEquals(60_000L, ReflectionTestUtils.getField(service, "budgetMs"));
        assertEquals(25_000, ReflectionTestUtils.getField(service, "readTimeoutMs"));
        assertEquals("low", ReflectionTestUtils.getField(service, "thinkingLevel"));
    }

    @Test
    @DisplayName("AI_READ_TIMEOUT_MS is applied to the HTTP client")
    void readTimeoutIsApplied() {
        ReflectionTestUtils.setField(service, "readTimeoutMs", 31_000);
        service.applyReadTimeout();
        Object factory = ReflectionTestUtils.getField(service, "requestFactory");
        assertEquals(31_000, ReflectionTestUtils.getField(factory, "readTimeout"));
    }

    @Test
    @DisplayName("GEMINI_FALLBACK_MODELS is parsed: trimmed, blanks and the primary model removed")
    void fallbackListParsing() {
        ReflectionTestUtils.setField(service, "fallbackModels", " gemini-3.6-flash , ,gemini-3.8-flash, gemini-x ");
        assertEquals(List.of("gemini-3.8-flash", "gemini-3.6-flash", "gemini-x"), service.geminiModels());
        ReflectionTestUtils.setField(service, "fallbackModels", "");
        assertEquals(List.of("gemini-3.8-flash"), service.geminiModels());
    }

    @Test
    @DisplayName("Defaults: fallbacks are gemini-3.6-flash and gemini-3.5-flash-lite")
    void defaultFallbacks() {
        assertEquals(List.of("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite"), service.geminiModels());
    }

    @Test
    @DisplayName("With AI_PROVIDER=groq the chain starts with Groq and ends with the Gemini models")
    void groqFirstChain() {
        ReflectionTestUtils.setField(service, "groqApiKey", "gsk-test");
        ReflectionTestUtils.setField(service, "providerSetting", "groq");
        List<AIAdvisorService.Target> chain = service.buildChain();
        assertEquals(AiProvider.GROQ, chain.get(0).provider());
        assertEquals(AiProvider.GEMINI, chain.get(1).provider());
        assertEquals(4, chain.size());
    }
}
