package com.gramsetu.util;

import com.gramsetu.util.AnswerLanguage.Detected;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnswerLanguageTest {

    @Test
    @DisplayName("Tamil script is Tamil, even mixed with English words")
    void tamilScript() {
        assertEquals(Detected.TAMIL, AnswerLanguage.detect("தக்காளி இலை சுருள்கிறது"));
        assertEquals(Detected.TAMIL, AnswerLanguage.detect("PM Kisan திட்டத்தில் எப்படி சேருவது?"));
        assertEquals(Detected.TAMIL, AnswerLanguage.detect("how do I தண்ணீர் விடணும்"));
        assertTrue(AnswerLanguage.hasTamil("a தமிழ்"));
        assertFalse(AnswerLanguage.hasTamil("english only"));
        assertFalse(AnswerLanguage.hasTamil(null));
    }

    @Test
    @DisplayName("Clearly English questions are English")
    void clearlyEnglish() {
        assertEquals(Detected.ENGLISH, AnswerLanguage.detect("How do I join the PM Kisan scheme?"));
        assertEquals(Detected.ENGLISH, AnswerLanguage.detect("My tomato leaves are curling and there are white flies"));
        assertEquals(Detected.ENGLISH, AnswerLanguage.detect("Which fertiliser should I use this month?"));
        assertEquals(Detected.ENGLISH, AnswerLanguage.detect("what is the best time to plant paddy"));
        assertEquals(Detected.ENGLISH, AnswerLanguage.detect("Is there a loan for farmers?"));
    }

    @Test
    @DisplayName("Short English greetings and phrases are English")
    void shortEnglish() {
        assertEquals(Detected.ENGLISH, AnswerLanguage.detect("hello"));
        assertEquals(Detected.ENGLISH, AnswerLanguage.detect("Thanks!"));
        assertEquals(Detected.ENGLISH, AnswerLanguage.detect("my crops"));
    }

    @Test
    @DisplayName("Tamil typed in English letters, numbers, emoji and 'ok' are unclear, not English")
    void unclear() {
        assertEquals(Detected.UNCLEAR, AnswerLanguage.detect("en thakkali ilai surundu pogudhu enna pannalam"));
        assertEquals(Detected.UNCLEAR, AnswerLanguage.detect("nel nadavu epo seiyalam"));
        assertEquals(Detected.UNCLEAR, AnswerLanguage.detect("ok"));
        assertEquals(Detected.UNCLEAR, AnswerLanguage.detect("2"));
        assertEquals(Detected.UNCLEAR, AnswerLanguage.detect("👍"));
        assertEquals(Detected.UNCLEAR, AnswerLanguage.detect("   "));
        assertEquals(Detected.UNCLEAR, AnswerLanguage.detect(""));
        assertEquals(Detected.UNCLEAR, AnswerLanguage.detect(null));
    }

    @Test
    @DisplayName("Website: the English button gives English; otherwise clearly English text gives English; else Tamil")
    void web() {
        assertEquals("english", AnswerLanguage.forWeb("english", "தக்காளி"));          // button wins
        assertEquals("english", AnswerLanguage.forWeb("english", "ok"));
        assertEquals("english", AnswerLanguage.forWeb("tamil", "How do I join the PM Kisan scheme?"));   // English question, Tamil button
        assertEquals("tamil", AnswerLanguage.forWeb("tamil", "தக்காளி இலை சுருள்கிறது"));
        assertEquals("tamil", AnswerLanguage.forWeb("tamil", "en thakkali ilai surundu pogudhu"));       // Tamil in English letters
        assertEquals("tamil", AnswerLanguage.forWeb("tamil", "ok"));
        assertEquals("tamil", AnswerLanguage.forWeb("tamil", null));
    }

    @Test
    @DisplayName("WhatsApp: the question's script decides; the saved command only breaks ties")
    void whatsApp() {
        // script is the main rule, whatever the saved command says
        assertEquals("tamil", AnswerLanguage.forWhatsApp("english", "நெல்லுக்கு உரம் என்ன?"));
        assertEquals("english", AnswerLanguage.forWhatsApp("tamil", "How much water does paddy need?"));
        assertEquals("english", AnswerLanguage.forWhatsApp(null, "How much water does paddy need?"));
        // unclear text: the saved command decides, and Tamil is the default
        assertEquals("english", AnswerLanguage.forWhatsApp("english", "ok"));
        assertEquals("english", AnswerLanguage.forWhatsApp("english", "2"));
        assertEquals("tamil", AnswerLanguage.forWhatsApp("tamil", "ok"));
        assertEquals("tamil", AnswerLanguage.forWhatsApp(null, "ok"));
        assertEquals("english", AnswerLanguage.forWhatsApp("english", "en thakkali ilai surundu"));   // typed Tamil, English chosen
    }
}
