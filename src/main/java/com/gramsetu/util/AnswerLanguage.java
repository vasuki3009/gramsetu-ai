package com.gramsetu.util;

import java.util.Locale;
import java.util.Set;

/**
 * Decides whether an answer is written in English or Tamil.
 *
 * <ul>
 *   <li><b>Website:</b> English if the language button is English, or if the question has no Tamil script and is
 *       clearly English. Tamil otherwise.</li>
 *   <li><b>WhatsApp:</b> the question decides: Tamil script means Tamil, clearly English means English. When the text
 *       is unclear ("ok", numbers, Tamil typed in English letters) the saved {@code english} / {@code tamil}
 *       command decides, and without one it is Tamil.</li>
 * </ul>
 *
 * "Clearly English" means it contains common English words ("how", "my", "the"...) in a fair share of its words.
 * Tamil written in Latin letters has almost none of them, so it is not mistaken for English; when in doubt the
 * answer is Tamil. Nothing here logs or stores the text.
 */
public final class AnswerLanguage {

    private AnswerLanguage() {}

    public static final String ENGLISH = "english";
    public static final String TAMIL = "tamil";

    /** What the text itself says about its language. */
    public enum Detected { TAMIL, ENGLISH, UNCLEAR }

    /** Common English words that Tamil written in English letters does not use. */
    static final Set<String> ENGLISH_WORDS = Set.of(
            "the", "an", "is", "are", "was", "were", "be", "been", "am", "do", "does", "did", "have", "has", "had",
            "how", "what", "when", "where", "which", "why", "who", "whom", "whose", "can", "could", "will", "would",
            "should", "shall", "may", "might", "must", "i", "me", "my", "mine", "we", "our", "you", "your", "he", "she",
            "it", "they", "them", "this", "that", "these", "those", "to", "of", "for", "in", "on", "at", "with", "from",
            "by", "about", "and", "or", "but", "not", "if", "so", "any", "some", "much", "many", "more", "need",
            "want", "get", "give", "tell", "please", "there", "here", "also", "than", "then", "too", "very",
            "hello", "hi", "hey", "thanks", "thank", "yes", "no", "farm", "crop", "crops", "farmer", "farmers",
            "scheme", "schemes", "loan", "insurance", "subsidy", "water", "soil", "leaves", "leaf", "plant",
            "join", "apply", "best", "time", "month", "season", "disease", "pest", "pests");

    /** True when the text contains at least one Tamil letter (Unicode block U+0B80 to U+0BFF). */
    public static boolean hasTamil(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0B80 && c <= 0x0BFF) return true;
        }
        return false;
    }

    /** Tamil script means Tamil; clearly English means English; anything else is unclear. */
    public static Detected detect(String text) {
        if (text == null || text.isBlank()) return Detected.UNCLEAR;
        if (hasTamil(text)) return Detected.TAMIL;

        int words = 0;
        int englishWords = 0;
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z']+")) {
            String w = raw.replace("'", "");
            if (w.isEmpty()) continue;
            words++;
            if (ENGLISH_WORDS.contains(w)) englishWords++;
        }
        if (words == 0) return Detected.UNCLEAR;                         // numbers, emoji, punctuation
        if (words <= 2) return englishWords >= 1 ? Detected.ENGLISH : Detected.UNCLEAR;   // "hello", "my crops"
        // a real share of the words must be English, so a Tamil sentence with an English word or two stays unclear
        return englishWords >= 2 && englishWords * 5 >= words ? Detected.ENGLISH : Detected.UNCLEAR;
    }

    /** Website: the language button, or clearly English text. {@code toggle} is "english" or anything else. */
    public static String forWeb(String toggle, String text) {
        if (ENGLISH.equals(toggle)) return ENGLISH;
        return detect(text) == Detected.ENGLISH ? ENGLISH : TAMIL;
    }

    /** WhatsApp: the question's script decides; the saved command ({@code preference}) breaks ties. */
    public static String forWhatsApp(String preference, String text) {
        return switch (detect(text)) {
            case TAMIL -> TAMIL;
            case ENGLISH -> ENGLISH;
            case UNCLEAR -> ENGLISH.equals(preference) ? ENGLISH : TAMIL;
        };
    }
}
