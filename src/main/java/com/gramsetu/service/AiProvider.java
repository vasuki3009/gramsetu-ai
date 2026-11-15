package com.gramsetu.service;

import java.util.ArrayList;
import java.util.List;

/** Which AI service answers first. Chosen with the AI_PROVIDER environment variable. */
public enum AiProvider {
    GEMINI, GROQ;

    /** Parses "gemini" / "groq" (any case, whitespace allowed). Empty, null or unknown falls back to GEMINI. */
    public static AiProvider parse(String value) {
        if (value != null) {
            String v = value.trim().toLowerCase();
            if (v.equals("groq")) return GROQ;
        }
        return GEMINI;
    }

    /**
     * The providers to try, in order. The chosen one goes first if its key is set; the other follows as a
     * fallback if ITS key is set. An empty list means "use the built-in sample answers".
     */
    public static List<AiProvider> callOrder(AiProvider preferred, boolean geminiConfigured, boolean groqConfigured) {
        AiProvider other = preferred == GEMINI ? GROQ : GEMINI;
        List<AiProvider> order = new ArrayList<>(2);
        if (isConfigured(preferred, geminiConfigured, groqConfigured)) order.add(preferred);
        if (isConfigured(other, geminiConfigured, groqConfigured)) order.add(other);
        return order;
    }

    private static boolean isConfigured(AiProvider p, boolean gemini, boolean groq) {
        return p == GEMINI ? gemini : groq;
    }
}
