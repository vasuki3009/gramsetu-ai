package com.gramsetu.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvisorPromptsTest {

    @Test
    @DisplayName("The system prompt carries every required rule")
    void containsRequiredRules() {
        String p = AdvisorPrompts.systemPrompt("tamil", "KONGU");
        assertTrue(p.contains("Hosur and Krishnagiri"), "role and region");
        assertTrue(p.contains("numbered steps"), "short numbered steps");
        assertTrue(p.contains("exactly ONE short clarifying question"), "one clarifying question");
        assertTrue(p.contains("FIRST give 2 or 3 short, safe, general steps"), "general guidance before asking");
        assertTrue(p.contains("Never ask the same question twice"), "no repeated questions");
        assertTrue(p.contains("NEVER give exact pesticide"), "no doses");
        assertTrue(p.contains("Krishi Vigyan Kendra"), "points to the local office / KVK");
        assertTrue(p.contains("product label"), "product label");
        assertTrue(p.contains("NEVER invent government scheme"), "no invented scheme amounts or dates");
        assertTrue(p.contains("I am not sure"), "says plainly when unsure");
        assertTrue(p.contains("Greetings and small talk"), "short reply for greetings");
        assertTrue(p.contains("Tamil in Tamil script"), "Tamil by default");
    }

    @Test
    @DisplayName("The dialect line follows the detected dialect; English and unknown fall back to plain wording")
    void dialectLine() {
        assertTrue(AdvisorPrompts.systemPrompt("tamil", "KONGU").contains("Kongu Tamil"));
        assertTrue(AdvisorPrompts.systemPrompt("tamil", "madurai").contains("Madurai Tamil"));
        assertTrue(AdvisorPrompts.systemPrompt("tamil", "NELLAI").contains("Nellai"));
        assertTrue(AdvisorPrompts.systemPrompt("tamil", "CHENNAI").contains("Chennai Tamil"));
        assertTrue(AdvisorPrompts.systemPrompt("tamil", "STANDARD").contains("standard spoken Tamil"));
        assertTrue(AdvisorPrompts.systemPrompt("tamil", null).contains("standard spoken Tamil"));
        // an English question never gets a regional Tamil style
        assertFalse(AdvisorPrompts.systemPrompt("english", "KONGU").contains("Kongu Tamil"));
    }

    @Test
    @DisplayName("The prompt sets no numeric dose of its own")
    void promptHasNoDoseExamples() {
        String p = AdvisorPrompts.systemPrompt("tamil", "KONGU").toLowerCase();
        assertFalse(p.matches("(?s).*\\d+\\s*(ml|mg|kg|gram|litre).*"));
    }
}
