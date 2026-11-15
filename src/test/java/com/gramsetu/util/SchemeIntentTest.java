package com.gramsetu.util;

import com.gramsetu.dto.SchemeResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchemeIntentTest {

    @Test
    void schemeQuestionsAreRecognisedInTamilAndEnglish() {
        assertTrue(SchemeIntent.isSchemeQuestion("What government schemes are there for farmers?"));
        assertTrue(SchemeIntent.isSchemeQuestion("Is there any subsidy for drip irrigation"));
        assertTrue(SchemeIntent.isSchemeQuestion("How do I get a crop LOAN"));
        assertTrue(SchemeIntent.isSchemeQuestion("crop insurance details"));
        assertTrue(SchemeIntent.isSchemeQuestion("PM Kisan"));
        assertTrue(SchemeIntent.isSchemeQuestion("விவசாயிகளுக்கு என்ன திட்டங்கள் உள்ளன"));
        assertTrue(SchemeIntent.isSchemeQuestion("சொட்டு நீர்ப்பாசனத்துக்கு மானியம் கிடைக்குமா"));
        assertTrue(SchemeIntent.isSchemeQuestion("விவசாய கடன் எப்படி வாங்குவது"));
        assertTrue(SchemeIntent.isSchemeQuestion("பயிர் காப்பீடு பற்றி சொல்லுங்கள்"));
    }

    @Test
    void greetingsAndFarmingQuestionsAreNot() {
        assertFalse(SchemeIntent.isSchemeQuestion("ஹலோ"));
        assertFalse(SchemeIntent.isSchemeQuestion("வணக்கம்"));
        assertFalse(SchemeIntent.isSchemeQuestion("Hello"));
        assertFalse(SchemeIntent.isSchemeQuestion("thank you"));
        assertFalse(SchemeIntent.isSchemeQuestion("தக்காளி இலை சுருள்கிறது, என்ன பண்றது?"));
        assertFalse(SchemeIntent.isSchemeQuestion("How much water does paddy need?"));
        assertFalse(SchemeIntent.isSchemeQuestion(""));
        assertFalse(SchemeIntent.isSchemeQuestion(null));
    }

    @Test
    void headingFollowsTheAnswersLanguage() {
        assertEquals("Matching Government Schemes:", SchemeIntent.heading("english", "English answer"));
        assertEquals("பொருத்தமான அரசு திட்டங்கள்:", SchemeIntent.heading("tamil", "தமிழ் பதில்"));
        assertEquals("பொருத்தமான அரசு திட்டங்கள்:", SchemeIntent.heading("tamil", "answer without Tamil letters"));
    }

    @Test
    void tamilScriptInTheAnswerGivesTheTamilHeadingEvenInEnglishMode() {
        assertEquals("பொருத்தமான அரசு திட்டங்கள்:", SchemeIntent.heading("english", "இது தமிழ் பதில்"));
        assertEquals("பொருத்தமான அரசு திட்டங்கள்:", SchemeIntent.heading("english", "Mixed answer with தமிழ்"));
        assertEquals("Matching Government Schemes:", SchemeIntent.heading("english", null));
        assertTrue(SchemeIntent.containsTamil("தமிழ்"));
        assertFalse(SchemeIntent.containsTamil("Tamil only in English letters"));
        assertFalse(SchemeIntent.containsTamil(null));
    }

    @Test
    void appendSchemesAddsListOrLeavesAnswerAlone() {
        List<SchemeResponse> schemes = List.of(new SchemeResponse("A", "", "", "", null), new SchemeResponse("B", "", "", "", null));
        assertEquals("answer\n\nபொருத்தமான அரசு திட்டங்கள்: \n• A\n• B", SchemeIntent.appendSchemes("answer", schemes, "tamil"));
        assertTrue(SchemeIntent.appendSchemes("answer", schemes, "english").contains("Matching Government Schemes:"));
        assertEquals("answer", SchemeIntent.appendSchemes("answer", List.of(), "tamil"));
        assertEquals("answer", SchemeIntent.appendSchemes("answer", null, "tamil"));
    }
}
