package com.gramsetu.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Request building and response parsing, without any network call. */
@SuppressWarnings("unchecked")
class AIAdvisorServiceRequestTest {

    @Test
    @DisplayName("Gemini request: system prompt in systemInstruction, temperature 0.2, an output limit, no search tool")
    void geminiBody() {
        Map<String, Object> body = AIAdvisorService.buildGeminiBody("SYSTEM", "QUESTION", 0.2, 2048);

        Map<String, Object> sys = (Map<String, Object>) body.get("systemInstruction");
        assertEquals("SYSTEM", ((Map<String, Object>) ((List<?>) sys.get("parts")).get(0)).get("text"));

        Map<String, Object> user = (Map<String, Object>) ((List<?>) body.get("contents")).get(0);
        assertEquals("user", user.get("role"));
        assertEquals("QUESTION", ((Map<String, Object>) ((List<?>) user.get("parts")).get(0)).get("text"));

        Map<String, Object> cfg = (Map<String, Object>) body.get("generationConfig");
        assertEquals(0.2, (double) cfg.get("temperature"), 1e-9);
        assertEquals(2048, cfg.get("maxOutputTokens"));
        assertFalse(body.containsKey("tools"), "Google Search grounding is not enabled (not on the free tier)");
    }

    @Test
    @DisplayName("Groq request: system message first, then the user message")
    void groqBody() {
        Map<String, Object> body = AIAdvisorService.buildGroqBody("SYSTEM", "QUESTION", 0.2, 2048);
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        assertEquals("system", messages.get(0).get("role"));
        assertEquals("SYSTEM", messages.get(0).get("content"));
        assertEquals("user", messages.get(1).get("role"));
        assertEquals(2048, body.get("max_tokens"));
    }

    @Test
    @DisplayName("History reaches the Gemini body: earlier turns first (assistant = model), the new question last")
    void geminiBodyWithHistory() {
        List<ChatTurn> history = List.of(new ChatTurn("user", "tomato leaf curl"), new ChatTurn("assistant", "which stage?"));
        Map<String, Object> body = AIAdvisorService.buildGeminiBody("SYSTEM", history, "white flies", 0.2, 2048, null);

        List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");
        assertEquals(3, contents.size());
        assertEquals("user", contents.get(0).get("role"));
        assertEquals("tomato leaf curl", ((Map<String, Object>) ((List<?>) contents.get(0).get("parts")).get(0)).get("text"));
        assertEquals("model", contents.get(1).get("role"));
        assertEquals("which stage?", ((Map<String, Object>) ((List<?>) contents.get(1).get("parts")).get(0)).get("text"));
        assertEquals("user", contents.get(2).get("role"));
        assertEquals("white flies", ((Map<String, Object>) ((List<?>) contents.get(2).get("parts")).get(0)).get("text"));
    }

    @Test
    @DisplayName("History reaches the Groq body: system, earlier turns, then the new question")
    void groqBodyWithHistory() {
        List<ChatTurn> history = List.of(new ChatTurn("user", "tomato leaf curl"), new ChatTurn("assistant", "which stage?"));
        Map<String, Object> body = AIAdvisorService.buildGroqBody("SYSTEM", history, "white flies", 0.2, 2048);

        List<Map<String, Object>> m = (List<Map<String, Object>>) body.get("messages");
        assertEquals(4, m.size());
        assertEquals("system", m.get(0).get("role"));
        assertEquals("user", m.get(1).get("role"));
        assertEquals("tomato leaf curl", m.get(1).get("content"));
        assertEquals("assistant", m.get(2).get("role"));
        assertEquals("user", m.get(3).get("role"));
        assertEquals("white flies", m.get(3).get("content"));
    }

    @Test
    @DisplayName("Null or empty history gives the same single-message body as before")
    void emptyHistory() {
        assertEquals(1, ((List<?>) AIAdvisorService.buildGeminiBody("S", null, "Q", 0.2, 10, null).get("contents")).size());
        assertEquals(2, ((List<?>) AIAdvisorService.buildGroqBody("S", List.of(), "Q", 0.2, 10).get("messages")).size());
    }

    @Test
    @DisplayName("Sample answers are recognised as fallbacks, real answers are not")
    void fallbackDetection() {
        assertTrue(AIAdvisorService.isFallbackAnswer(AIAdvisorService.BUSY_NOTE_TAMIL + "\n\nsample"));
        assertTrue(AIAdvisorService.isFallbackAnswer(AIAdvisorService.BUSY_NOTE_ENGLISH + "\n\nsample"));
        assertFalse(AIAdvisorService.isFallbackAnswer("1. Check drainage."));
        assertFalse(AIAdvisorService.isFallbackAnswer(null));
    }

    @Test
    @DisplayName("Gemini response: answer parts are joined, thought parts are skipped, blocked answers are null")
    void extractText() {
        Map<String, Object> ok = Map.of("candidates", List.of(Map.of(
                "finishReason", "STOP",
                "content", Map.of("parts", List.of(
                        Map.of("text", "thinking...", "thought", true),
                        Map.of("text", "1. "), Map.of("text", "Check drainage."))))));
        assertEquals("1. Check drainage.", AIAdvisorService.extractGeminiText(ok));

        assertNull(AIAdvisorService.extractGeminiText(Map.of("candidates", List.of(Map.of("finishReason", "SAFETY")))));
        assertNull(AIAdvisorService.extractGeminiText(Map.of()));
        assertNull(AIAdvisorService.extractGeminiText(null));
    }

    @Test
    @DisplayName("Defaults: Gemini provider, a current model, and the sample answers carry no doses")
    void defaults() {
        AIAdvisorService svc = new AIAdvisorService();
        assertEquals(AiProvider.GEMINI, svc.getProvider());
        assertEquals("gemini-3.8-flash", svc.getGeminiModel());
        String demo = svc.getDemoAnswer("நெல் பூச்சி", "tamil");
        assertFalse(demo.matches("(?s).*\\d+\\s*(கிராம்|கிலோ|மிலி).*"), "no numeric doses in sample answers");
        assertTrue(demo.contains("வேளாண் அலுவலகத்தில்"), "points to the agriculture office");
    }
}
