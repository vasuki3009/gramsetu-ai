package com.gramsetu.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TtsTextTest {

    private static List<String> words(String s) {
        return s.isBlank() ? List.of() : Arrays.asList(s.trim().split("\\s+"));
    }

    private static String sentence(int n) {
        return "இது " + n + " ஆம் வாக்கியம் ஆகும்.";
    }

    // ── cleaning ──

    @Test
    @DisplayName("Markdown symbols are removed, the words stay")
    void removesMarkdown() {
        String raw = "# தலைப்பு\n**முக்கியம்:** *நீர்* பாய்ச்சவும் `கவனம்` ___மெதுவாக___ [இணைப்பு](https://example.invalid/x)\n---\n";
        String cleaned = TtsText.clean(raw);
        assertEquals("தலைப்பு\nமுக்கியம்: நீர் பாய்ச்சவும் கவனம் மெதுவாக இணைப்பு", cleaned);
        for (String bad : List.of("*", "#", "`", "_", "[", "](", "---")) {
            assertFalse(cleaned.contains(bad), "left behind: " + bad);
        }
    }

    @Test
    @DisplayName("List markers (numbers and bullets) are dropped, numbers inside a sentence are kept")
    void removesListMarkers() {
        String raw = "1. நீர் பாய்ச்சவும்\n2) களை எடுக்கவும்\n- இலையை அகற்றவும்\n• மருந்து தெளிக்கவும்\n* பூச்சி பார்க்கவும்\n5 ஏக்கருக்கு 2.5 கிலோ";
        assertEquals("நீர் பாய்ச்சவும்\nகளை எடுக்கவும்\nஇலையை அகற்றவும்\nமருந்து தெளிக்கவும்\nபூச்சி பார்க்கவும்\n5 ஏக்கருக்கு 2.5 கிலோ",
                TtsText.clean(raw));
    }

    @Test
    @DisplayName("Emoji and symbols are removed")
    void removesEmoji() {
        assertEquals("எச்சரிக்கை நல்ல பதில் வேகமாக", TtsText.clean("⚠️ எச்சரிக்கை 🌿 நல்ல 🔴 பதில் ✅ வேகமாக 🗣️"));
    }

    @Test
    @DisplayName("Whitespace is tidied, blank lines vanish, null and blank give an empty string")
    void tidiesWhitespace() {
        assertEquals("ஒன்று இரண்டு\nமூன்று", TtsText.clean("  ஒன்று \t  இரண்டு  \n\n\n   மூன்று  "));
        assertEquals("", TtsText.clean(null));
        assertEquals("", TtsText.clean("  \n \n"));
        assertEquals("", TtsText.clean("**  **"));
    }

    // ── splitting ──

    @Test
    @DisplayName("Short text is one chunk")
    void shortTextOneChunk() {
        assertEquals(List.of("வணக்கம். எப்படி இருக்கிறீர்கள்?"), TtsText.split("வணக்கம். எப்படி இருக்கிறீர்கள்?", 100));
    }

    @Test
    @DisplayName("Every chunk is within the limit, order is kept and no text is lost")
    void limitOrderAndNoLoss() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 40; i++) sb.append(sentence(i)).append(i % 5 == 0 ? "\n" : " ");
        String cleaned = TtsText.clean(sb.toString());

        for (int max : new int[] {30, 60, 100, 250}) {
            List<String> chunks = TtsText.split(cleaned, max);
            assertTrue(chunks.size() > 1, "max " + max);
            for (String c : chunks) {
                assertTrue(c.length() <= max, "chunk of " + c.length() + " exceeds " + max);
                assertFalse(c.isBlank());
                assertEquals(c.trim(), c, "chunks carry no outer whitespace");
            }
            assertEquals(words(cleaned), words(String.join(" ", chunks)), "words lost, split or reordered at max " + max);
        }
    }

    @Test
    @DisplayName("Sentences are kept whole when they fit: chunks end at a sentence end")
    void chunksEndAtSentenceEnds() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 12; i++) sb.append(sentence(i)).append(' ');
        List<String> chunks = TtsText.split(sb.toString().trim(), 70);
        assertTrue(chunks.size() > 1);
        for (String c : chunks) assertTrue(c.endsWith("."), "chunk does not end at a sentence end: " + c);
    }

    @Test
    @DisplayName("Question marks, exclamation marks, the Tamil danda and line breaks also end a sentence")
    void otherSentenceEnds() {
        List<String> chunks = TtsText.split("முதல் கேள்வி? இரண்டாவது! மூன்றாவது। நான்காவது\nஐந்தாவது", 14);
        assertEquals(List.of("முதல் கேள்வி?", "இரண்டாவது!", "மூன்றாவது।", "நான்காவது", "ஐந்தாவது"), chunks);
    }

    @Test
    @DisplayName("A long sentence is cut at a comma first, never inside a word")
    void longSentenceCutAtComma() {
        String s = "நீர் பாய்ச்சவும் காலையில் நல்லது, களை எடுக்கவும் மாலையில் நல்லது, இலையை அகற்றவும் தினமும் பார்க்கவும்";
        List<String> chunks = TtsText.split(s, 45);
        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.get(0).endsWith(","), chunks.get(0));
        for (String c : chunks) assertTrue(c.length() <= 45);
        assertEquals(words(s), words(String.join(" ", chunks)));
    }

    @Test
    @DisplayName("A long sentence without punctuation is cut at spaces, never mid-word")
    void longSentenceCutAtSpaces() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) sb.append("பயிர்").append(i).append(' ');
        String s = sb.toString().trim();
        List<String> chunks = TtsText.split(s, 100);
        assertTrue(chunks.size() > 10);
        for (String c : chunks) assertTrue(c.length() <= 100);
        assertEquals(words(s), words(String.join(" ", chunks)), "a word was cut");
    }

    @Test
    @DisplayName("A single word longer than the limit is the only thing that is cut hard, and nothing is lost")
    void hugeWordCutHard() {
        String word = "அ".repeat(250);
        List<String> chunks = TtsText.split(word, 100);
        assertEquals(List.of(100, 100, 50), chunks.stream().map(String::length).toList());
        assertEquals(word, String.join("", chunks));
    }

    @Test
    @DisplayName("A short sentence after a long one can share its last chunk")
    void tailPacksWithNextSentence() {
        String longSentence = "சொல் ".repeat(30).trim();   // 149 chars, no punctuation
        List<String> chunks = TtsText.split(longSentence + ". முடிவு.", 100);
        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.get(chunks.size() - 1).endsWith("முடிவு."));
        for (String c : chunks) assertTrue(c.length() <= 100);
    }

    @Test
    @DisplayName("Numbered steps: the numbers are gone and no chunk starts with one")
    void listNumbersDoNotLeak() {
        String raw = "1. முதல் படி நீர் பாய்ச்சவும்\n2. இரண்டாம் படி களை எடுக்கவும்\n3. மூன்றாம் படி இலையை அகற்றவும்";
        List<String> chunks = TtsText.chunks(raw, 40);
        assertFalse(chunks.isEmpty());
        for (String c : chunks) {
            assertFalse(c.matches("^\\d.*"), c);
            assertFalse(c.contains("1.") || c.contains("2.") || c.contains("3."), c);
        }
        assertEquals(words("முதல் படி நீர் பாய்ச்சவும் இரண்டாம் படி களை எடுக்கவும் மூன்றாம் படி இலையை அகற்றவும்"),
                words(String.join(" ", chunks)));
    }

    @Test
    @DisplayName("Empty, blank and null input give no chunks; a non-positive limit is rejected")
    void edgeCases() {
        assertTrue(TtsText.split("", 100).isEmpty());
        assertTrue(TtsText.split("   ", 100).isEmpty());
        assertTrue(TtsText.split(null, 100).isEmpty());
        assertTrue(TtsText.chunks("**", 100).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> TtsText.split("x", 0));
    }
}
