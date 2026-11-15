package com.gramsetu.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMemoryTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private ConversationMemory memory;

    @BeforeEach
    void setUp() {
        memory = new ConversationMemory(now::get, ConversationMemory.EXPIRY_MS, 3);
    }

    @Test
    @DisplayName("Unknown number has no history")
    void emptyByDefault() {
        assertTrue(memory.history("n1").isEmpty());
    }

    @Test
    @DisplayName("Question and answer are stored in order, oldest first")
    void storesPairs() {
        memory.remember("n1", "tomato leaf curl", "check whiteflies");
        assertEquals(List.of(new ChatTurn("user", "tomato leaf curl"), new ChatTurn("assistant", "check whiteflies")),
                memory.history("n1"));
    }

    @Test
    @DisplayName("Only the last 6 messages (3 pairs) are kept")
    void trimsToSixMessages() {
        for (int i = 1; i <= 5; i++) memory.remember("n1", "q" + i, "a" + i);
        List<ChatTurn> h = memory.history("n1");
        assertEquals(6, h.size());
        assertEquals("q3", h.get(0).text());
        assertEquals("a5", h.get(5).text());
    }

    @Test
    @DisplayName("A conversation expires after 30 minutes of inactivity")
    void expires() {
        memory.remember("n1", "q", "a");
        now.addAndGet(ConversationMemory.EXPIRY_MS - 1);
        assertEquals(2, memory.history("n1").size());   // still alive (reading does not extend it)
        now.addAndGet(1);
        assertTrue(memory.history("n1").isEmpty());
        assertEquals(0, memory.size(), "expired entry is removed");
    }

    @Test
    @DisplayName("A new message after expiry starts a fresh conversation")
    void freshAfterExpiry() {
        memory.remember("n1", "old q", "old a");
        now.addAndGet(ConversationMemory.EXPIRY_MS + 1);
        memory.remember("n1", "new q", "new a");
        assertEquals(List.of("new q", "new a"), memory.history("n1").stream().map(ChatTurn::text).toList());
    }

    @Test
    @DisplayName("Activity keeps a conversation alive")
    void activityExtends() {
        memory.remember("n1", "q1", "a1");
        now.addAndGet(ConversationMemory.EXPIRY_MS - 1000);
        memory.remember("n1", "q2", "a2");
        now.addAndGet(ConversationMemory.EXPIRY_MS - 1000);
        assertEquals(4, memory.history("n1").size());
    }

    @Test
    @DisplayName("The map is bounded: the least recently used number is dropped")
    void lruBound() {
        memory.remember("n1", "q", "a");
        memory.remember("n2", "q", "a");
        memory.remember("n3", "q", "a");
        memory.history("n1");                 // touch n1 so n2 becomes the eldest
        memory.remember("n4", "q", "a");
        assertEquals(3, memory.size());
        assertTrue(memory.history("n2").isEmpty(), "n2 was evicted");
        assertFalse(memory.history("n1").isEmpty());
        assertFalse(memory.history("n3").isEmpty());
        assertFalse(memory.history("n4").isEmpty());
    }

    @Test
    @DisplayName("The default bound is 2000 numbers")
    void defaultBound() {
        ConversationMemory big = new ConversationMemory();
        for (int i = 0; i < ConversationMemory.MAX_NUMBERS + 50; i++) big.remember("n" + i, "q", "a");
        assertEquals(ConversationMemory.MAX_NUMBERS, big.size());
    }

    @Test
    @DisplayName("clear() forgets one number only")
    void clear() {
        memory.remember("n1", "q", "a");
        memory.remember("n2", "q", "a");
        memory.clear("n1");
        assertTrue(memory.history("n1").isEmpty());
        assertFalse(memory.history("n2").isEmpty());
    }

    @Test
    @DisplayName("The returned history is a copy")
    void historyIsCopy() {
        memory.remember("n1", "q", "a");
        memory.history("n1").clear();
        assertEquals(2, memory.history("n1").size());
    }

    @Test
    @DisplayName("Reset keywords: reset, புதிதாக, புது கேள்வி (any case, spaces, trailing punctuation)")
    void resetKeywords() {
        assertTrue(ConversationMemory.isResetKeyword("reset"));
        assertTrue(ConversationMemory.isResetKeyword("  RESET! "));
        assertTrue(ConversationMemory.isResetKeyword("புதிதாக"));
        assertTrue(ConversationMemory.isResetKeyword("புது கேள்வி"));
        assertTrue(ConversationMemory.isResetKeyword("புது   கேள்வி."));
        assertFalse(ConversationMemory.isResetKeyword("reset my tomato advice"));
        assertFalse(ConversationMemory.isResetKeyword("புது"));
        assertFalse(ConversationMemory.isResetKeyword(""));
        assertFalse(ConversationMemory.isResetKeyword(null));
    }

    @Test
    @DisplayName("Greetings are recognised, farming questions are not")
    void greetings() {
        assertTrue(ConversationMemory.isGreeting("Hello!"));
        assertTrue(ConversationMemory.isGreeting("hi"));
        assertTrue(ConversationMemory.isGreeting("வணக்கம்"));
        assertTrue(ConversationMemory.isGreeting("Thank you 🙏"));
        assertTrue(ConversationMemory.isGreeting("நன்றி"));
        assertFalse(ConversationMemory.isGreeting("hello my tomato leaves are curling"));
        assertFalse(ConversationMemory.isGreeting("தக்காளி இலை சுருள்கிறது"));
        assertFalse(ConversationMemory.isGreeting(""));
    }
}
