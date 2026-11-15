package com.gramsetu.util;

import com.gramsetu.dto.SchemeResponse;
import com.gramsetu.service.ConversationMemory;

import java.util.List;
import java.util.Locale;

/**
 * Decides whether a farmer's question is about government schemes, subsidies, loans, insurance and the like, and
 * appends the matching-scheme list to an answer only then. Greetings and ordinary farming questions get no list.
 * Matching is keyword-based (Tamil and English) on word fragments, so "கடனுதவி" matches "கடன்" and "subsidies" matches "subsid".
 */
public final class SchemeIntent {

    private SchemeIntent() {}

    /** Lower-case fragments; Tamil fragments are the word stems that carry the case endings. */
    static final List<String> KEYWORDS = List.of(
            // English
            "scheme", "subsid", "loan", "insurance", "pension", "kisan", "credit card", "government aid", "govt aid",
            // Tamil: scheme(s), subsidy, loan, insurance, pension, assistance money
            "திட்ட", "மானிய", "கடன்", "காப்பீடு", "ஓய்வூதிய", "உதவித்தொகை", "கிசான்");

    /** True when the question asks about schemes, subsidies, loans, insurance or similar. Never true for a greeting. */
    public static boolean isSchemeQuestion(String question) {
        if (question == null || question.isBlank() || ConversationMemory.isGreeting(question)) return false;
        String lower = question.toLowerCase(Locale.ROOT);
        for (String k : KEYWORDS) if (lower.contains(k)) return true;
        return false;
    }

    public static final String HEADING_TAMIL = "பொருத்தமான அரசு திட்டங்கள்:";
    public static final String HEADING_ENGLISH = "Matching Government Schemes:";

    /**
     * The heading of the appended list, in the language of the answer: Tamil unless the request language is English,
     * and always Tamil when the answer text itself contains Tamil script.
     */
    public static String heading(String language, String answer) {
        boolean english = "english".equals(language) && !containsTamil(answer);
        return english ? HEADING_ENGLISH : HEADING_TAMIL;
    }

    /** True when the text has at least one character of the Tamil Unicode block (U+0B80 to U+0BFF). */
    static boolean containsTamil(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0B80 && c <= 0x0BFF) return true;
        }
        return false;
    }

    /** The answer, followed by the scheme names under a heading in the answer's language. Unchanged when there are none. */
    public static String appendSchemes(String answer, List<SchemeResponse> schemes, String language) {
        if (schemes == null || schemes.isEmpty()) return answer;
        StringBuilder sb = new StringBuilder(answer).append("\n\n").append(heading(language, answer)).append(' ');
        for (SchemeResponse s : schemes) sb.append("\n• ").append(s.getSchemeName());
        return sb.toString();
    }
}
