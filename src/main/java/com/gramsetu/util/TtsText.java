package com.gramsetu.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Prepares an answer for text-to-speech: removes markdown and list numbering noise, then splits it into
 * sentence-sized chunks that each fit the provider's per-request limit. Pure functions, no logging: the text
 * handled here is the farmer's answer and must never reach a log.
 */
public final class TtsText {

    private TtsText() {}

    private static final Pattern HEADING = Pattern.compile("(?m)^[ \\t]{0,3}#{1,6}[ \\t]*");
    private static final Pattern RULE = Pattern.compile("(?m)^[ \\t]*(?:[-*_=][ \\t]*){3,}$");
    /** A bullet ("-", "•", "*"...) or a list number ("1.", "2)") at the start of a line. */
    private static final Pattern LIST_MARK = Pattern.compile("(?m)^[ \\t]*(?:[-+*•–·]|\\d{1,3}[.)])[ \\t]+");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]\\n]+)]\\([^)\\n]*\\)");
    private static final Pattern EMPHASIS = Pattern.compile("(\\*{1,3})([^*\\n]+?)\\1");
    private static final Pattern UNDERSCORE_EMPHASIS =
            Pattern.compile("(?<![\\p{L}\\p{N}])(_{1,3})([^_\\n]+?)\\1(?![\\p{L}\\p{N}])");
    /** Emoji and other symbols, variation selectors, zero-width joiner, keycap mark. */
    private static final Pattern SYMBOLS = Pattern.compile("[\\p{So}\\uFE0E\\uFE0F\\u200D\\u20E3]");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?।…])\\s+");

    /**
     * Plain text for speech. Line breaks are kept (one per line) because the splitter treats them as sentence
     * boundaries; markdown symbols, list markers, links, emoji and repeated spaces are removed.
     */
    public static String clean(String text) {
        if (text == null) return "";
        String s = text.replace(' ', ' ');
        s = HEADING.matcher(s).replaceAll("");
        s = RULE.matcher(s).replaceAll("");
        s = LIST_MARK.matcher(s).replaceAll("");
        s = LINK.matcher(s).replaceAll("$1");
        s = EMPHASIS.matcher(s).replaceAll("$2");
        s = UNDERSCORE_EMPHASIS.matcher(s).replaceAll("$2");
        s = s.replace("*", "").replace("`", "");
        s = SYMBOLS.matcher(s).replaceAll("");

        StringBuilder out = new StringBuilder();
        for (String line : s.split("\\R")) {
            String t = line.replaceAll("[ \\t\\x0B\\f]+", " ").trim();
            if (t.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(t);
        }
        return out.toString();
    }

    /** {@link #clean} then {@link #split}: the chunks to send to text-to-speech, in reading order. */
    public static List<String> chunks(String text, int maxChars) {
        return split(clean(text), maxChars);
    }

    /**
     * Splits already-cleaned text into chunks of at most {@code maxChars} characters, in order, without losing text.
     * Sentences (ending in . ! ? । … or a line break) are kept whole and packed together. A sentence longer than the
     * limit is cut at the last comma, semicolon or colon, else at the last space, so a word is never split; only a
     * single "word" longer than the limit is cut hard.
     */
    public static List<String> split(String cleaned, int maxChars) {
        if (maxChars < 1) throw new IllegalArgumentException("maxChars must be positive");
        List<String> chunks = new ArrayList<>();
        if (cleaned == null || cleaned.isBlank()) return chunks;

        StringBuilder current = new StringBuilder();
        for (String line : cleaned.split("\\R")) {
            for (String sentence : SENTENCE_END.split(line.trim())) {
                String s = sentence.trim();
                if (s.isEmpty()) continue;
                if (s.length() > maxChars) {
                    flush(chunks, current);
                    List<String> pieces = breakLong(s, maxChars);
                    for (int i = 0; i < pieces.size() - 1; i++) chunks.add(pieces.get(i));
                    current.append(pieces.get(pieces.size() - 1));   // the tail can still share a chunk with the next sentence
                } else if (current.length() == 0) {
                    current.append(s);
                } else if (current.length() + 1 + s.length() <= maxChars) {
                    current.append(' ').append(s);
                } else {
                    flush(chunks, current);
                    current.append(s);
                }
            }
        }
        flush(chunks, current);
        return chunks;
    }

    private static void flush(List<String> chunks, StringBuilder current) {
        if (current.length() > 0) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }

    /** Cuts one over-long sentence into pieces of at most {@code max} characters at punctuation or spaces. */
    private static List<String> breakLong(String sentence, int max) {
        List<String> pieces = new ArrayList<>();
        String rest = sentence;
        while (rest.length() > max) {
            int cut = lastPunctuation(rest, max);          // piece keeps the punctuation mark
            int next;
            if (cut >= max / 3) {
                next = cut + 1;
            } else {
                int space = rest.lastIndexOf(' ', max);    // piece excludes the space
                if (space > 0) {
                    cut = space - 1;
                    next = space + 1;
                } else {
                    cut = max - 1;                          // no space at all: a single huge "word", cut hard
                    next = max;
                }
            }
            pieces.add(rest.substring(0, cut + 1).trim());
            rest = rest.substring(next).trim();
        }
        if (!rest.isEmpty()) pieces.add(rest);
        return pieces;
    }

    /** Index of the last comma, semicolon or colon within the first {@code max} characters, or -1. */
    private static int lastPunctuation(String s, int max) {
        int limit = Math.min(max, s.length()) - 1;
        for (int i = limit; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ',' || c == ';' || c == ':' || c == '،') return i;
        }
        return -1;
    }
}
