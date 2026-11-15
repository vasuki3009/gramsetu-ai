package com.gramsetu.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** An English answer must be asked for in English, with no Tamil default and no Tamil dialect left in the prompt. */
class AdvisorPromptsEnglishTest {

    @Test
    @DisplayName("The English prompt says: English only, no Tamil script, no Tamil dialect")
    void englishPromptDemandsEnglish() {
        String p = AdvisorPrompts.systemPrompt("english", "KONGU");
        assertTrue(p.contains("Reply in plain, simple English only"), p);
        assertTrue(p.contains("Do not use Tamil script"), p);
        assertTrue(p.contains("highest priority"), p);
        assertTrue(p.contains("Plain, simple English. No Tamil dialect."), p);
    }

    @Test
    @DisplayName("The English prompt has no 'Default: Tamil', no dialect line and no Tamil letters")
    void englishPromptHasNoTamilDefault() {
        String p = AdvisorPrompts.systemPrompt("english", "KONGU");
        assertFalse(p.contains("Default: Tamil"), p);
        assertFalse(p.contains("Kongu Tamil"), "the dialect given must be ignored for English");
        assertFalse(p.contains("standard spoken Tamil"), p);
        assertFalse(AnswerLanguage.hasTamil(p), "no Tamil script anywhere in the English prompt");
    }

    @Test
    @DisplayName("The English prompt keeps all the safety and answer-shape rules")
    void englishPromptKeepsTheRules() {
        String p = AdvisorPrompts.systemPrompt("english", null);
        assertNotEquals(AdvisorPrompts.CORE_RULES, AdvisorPrompts.ENGLISH_RULES, "the language section was replaced");
        assertTrue(p.contains("NEVER give exact pesticide"));
        assertTrue(p.contains("exactly ONE short clarifying question"));
        assertTrue(p.contains("Greetings and small talk"));
        assertTrue(p.contains("Krishi Vigyan Kendra"));
    }

    @Test
    @DisplayName("The Tamil prompt is unchanged: Tamil by default, with the dialect style")
    void tamilPromptUnchanged() {
        String p = AdvisorPrompts.systemPrompt("tamil", "KONGU");
        assertTrue(p.contains("Default: Tamil in Tamil script"));
        assertTrue(p.contains("Kongu Tamil"));
        assertFalse(p.contains("highest priority"));
    }
}
