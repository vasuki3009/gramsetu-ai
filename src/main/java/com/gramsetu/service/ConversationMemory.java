package com.gramsetu.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Short-term memory of a WhatsApp conversation, so a follow-up such as "the leaves are curling" keeps the crop
 * from the earlier messages.
 *
 * <ul>
 *   <li>Memory only: nothing is written to a database or to disk, and it is lost on restart.</li>
 *   <li>The last {@value #MAX_MESSAGES} messages (3 question/answer pairs) per number.</li>
 *   <li>A conversation expires after 30 minutes without a new message.</li>
 *   <li>At most {@value #MAX_NUMBERS} numbers are kept; the least recently used one is dropped first.</li>
 * </ul>
 *
 * This class never logs: it holds phone numbers and message text.
 */
public class ConversationMemory {

    public static final int MAX_MESSAGES = 6;
    public static final int MAX_NUMBERS = 2_000;
    public static final long EXPIRY_MS = 30 * 60 * 1000L;

    /** Messages that clear the history (compared after trimming, lower-casing and removing trailing punctuation). */
    static final Set<String> RESET_KEYWORDS = Set.of("reset", "புதிதாக", "புது கேள்வி");

    /** Greetings and thanks. They carry no farming context, so they are not remembered. */
    private static final Set<String> GREETING_WORDS = Set.of(
            "hi", "hello", "hey", "hii", "helo", "thanks", "thank", "you", "ok", "okay", "bye", "good", "morning",
            "evening", "afternoon", "night", "vanakkam", "vanakam", "nandri",
            "வணக்கம்", "நன்றி", "ஹலோ", "ஹாய்", "சரி", "போய்வருகிறேன்");

    private static final class Conversation {
        final Deque<ChatTurn> turns = new ArrayDeque<>();
        long lastActiveMs;
    }

    private final long expiryMs;
    private final LongSupplier clock;
    private final Map<String, Conversation> conversations;

    public ConversationMemory() {
        this(System::currentTimeMillis, EXPIRY_MS, MAX_NUMBERS);
    }

    ConversationMemory(LongSupplier clock, long expiryMs, int maxNumbers) {
        this.clock = clock;
        this.expiryMs = expiryMs;
        // access order: get() marks a number as recently used, removeEldestEntry drops the least recently used one
        this.conversations = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Conversation> eldest) {
                return size() > maxNumbers;
            }
        };
    }

    /** The remembered messages, oldest first. Empty when there are none or the conversation has expired. */
    public synchronized List<ChatTurn> history(String number) {
        Conversation c = conversations.get(number);
        if (c == null) return List.of();
        if (clock.getAsLong() - c.lastActiveMs >= expiryMs) {
            conversations.remove(number);
            return List.of();
        }
        return new ArrayList<>(c.turns);
    }

    /** Remembers one question and its answer, then keeps only the newest {@value #MAX_MESSAGES} messages. */
    public synchronized void remember(String number, String question, String answer) {
        long now = clock.getAsLong();
        Conversation c = conversations.get(number);
        if (c == null || now - c.lastActiveMs >= expiryMs) {
            c = new Conversation();
            conversations.put(number, c);
        }
        c.turns.addLast(new ChatTurn(ChatTurn.USER, question));
        c.turns.addLast(new ChatTurn(ChatTurn.ASSISTANT, answer));
        while (c.turns.size() > MAX_MESSAGES) c.turns.removeFirst();
        c.lastActiveMs = now;
    }

    public synchronized void clear(String number) {
        conversations.remove(number);
    }

    /** Number of conversations currently held (expired ones may still be counted until they are touched). */
    public synchronized int size() {
        return conversations.size();
    }

    public static boolean isResetKeyword(String text) {
        return RESET_KEYWORDS.contains(normalise(text));
    }

    /** True for a short message made only of greeting/thanks words, such as "Hello!" or "வணக்கம்". */
    public static boolean isGreeting(String text) {
        String n = normalise(text);
        if (n.isEmpty()) return false;
        for (String word : n.split("\s+")) {
            if (!GREETING_WORDS.contains(word)) return false;
        }
        return true;
    }

    private static String normalise(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{M}\\p{N}\\s]", " ")   // punctuation and emoji → space (keeps Tamil letters and marks)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
