package com.gramsetu.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.gramsetu.service.AiProvider.GEMINI;
import static com.gramsetu.service.AiProvider.GROQ;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AiProviderTest {

    @Test
    @DisplayName("AI_PROVIDER defaults to gemini and ignores case, spaces and unknown values")
    void parse() {
        assertEquals(GEMINI, AiProvider.parse(null));
        assertEquals(GEMINI, AiProvider.parse(""));
        assertEquals(GEMINI, AiProvider.parse("gemini"));
        assertEquals(GEMINI, AiProvider.parse("something-else"));
        assertEquals(GROQ, AiProvider.parse("groq"));
        assertEquals(GROQ, AiProvider.parse("  GROQ "));
    }

    @Test
    @DisplayName("The chosen provider goes first, the other one is the fallback only if its key is set")
    void callOrder() {
        assertEquals(List.of(GEMINI, GROQ), AiProvider.callOrder(GEMINI, true, true));
        assertEquals(List.of(GROQ, GEMINI), AiProvider.callOrder(GROQ, true, true));
        // keys are kept, only the order changes: no need to delete a key to switch
        assertEquals(List.of(GEMINI), AiProvider.callOrder(GEMINI, true, false));
        assertEquals(List.of(GEMINI), AiProvider.callOrder(GROQ, true, false));   // Groq chosen but no Groq key
        assertEquals(List.of(GROQ), AiProvider.callOrder(GEMINI, false, true));   // Gemini chosen but no Gemini key
        assertEquals(List.of(), AiProvider.callOrder(GEMINI, false, false));       // -> sample answers
    }
}
