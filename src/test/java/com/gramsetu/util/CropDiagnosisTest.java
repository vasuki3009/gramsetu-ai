package com.gramsetu.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CropDiagnosisTest {

    private static final String CONFIDENT_EN = """
            Crop: Tomato
            Confidence: high
            Disease: Early blight (Alternaria solani)
            Symptoms: brown rings on lower leaves
            Treatment:
            1. Remove infected leaves
            Medicine: Mancozeb
            """;

    private static final String CONFIDENT_TA = """
            பயிர்: தக்காளி
            நம்பிக்கை: medium
            நோய்: இலை கருகல் நோய்
            சிகிச்சை:
            1. பாதித்த இலைகளை அகற்றவும்
            மருந்து: மான்கோசெப்
            """;

    @Test
    void confidentEnglishAnswer_isNotUnsure() {
        assertFalse(CropDiagnosis.isUnsure(CONFIDENT_EN));
        assertEquals("high", CropDiagnosis.confidence(CONFIDENT_EN));
        assertEquals("Tomato", CropDiagnosis.crop(CONFIDENT_EN));
    }

    @Test
    void confidentTamilAnswer_isNotUnsure() {
        assertFalse(CropDiagnosis.isUnsure(CONFIDENT_TA));
        assertEquals("medium", CropDiagnosis.confidence(CONFIDENT_TA));
    }

    @Test
    void markdownBoldLabels_stillParse() {
        assertFalse(CropDiagnosis.isUnsure(CONFIDENT_EN.replace("Crop:", "**Crop:**").replace("Confidence:", "**Confidence:**")
                .replace("Disease:", "**Disease:**")));
    }

    @Test
    void lowConfidence_isUnsure() {
        assertTrue(CropDiagnosis.isUnsure(CONFIDENT_EN.replace("high", "low")));
        assertTrue(CropDiagnosis.isUnsure("பயிர்: தெளிவில்லை\nநம்பிக்கை: low\nமுடிவு: எனக்கு உறுதியாகத் தெரியவில்லை."));
    }

    @Test
    void missingOrUnreadableConfidence_isUnsure() {
        assertTrue(CropDiagnosis.isUnsure(CONFIDENT_EN.replace("Confidence: high\n", "")));
        assertTrue(CropDiagnosis.isUnsure(CONFIDENT_EN.replace("high", "very sure")));
    }

    @Test
    void missingCropOrDisease_isUnsure() {
        assertTrue(CropDiagnosis.isUnsure(CONFIDENT_EN.replace("Crop: Tomato\n", "")));
        assertTrue(CropDiagnosis.isUnsure("Crop: Tomato\nConfidence: high\nSymptoms: spots"));
    }

    @Test
    void unclearCropEvenWithHighConfidence_isUnsure() {
        assertTrue(CropDiagnosis.isUnsure(CONFIDENT_EN.replace("Tomato", "not a crop leaf")));
        assertTrue(CropDiagnosis.isUnsure(CONFIDENT_EN.replace("Tomato", "unclear")));
    }

    @Test
    void notSurePhraseAnywhere_isUnsure() {
        assertTrue(CropDiagnosis.isUnsure(CONFIDENT_EN + "Result: I am not sure."));
    }

    @Test
    void nullOrBlank_isUnsure() {
        assertTrue(CropDiagnosis.isUnsure(null));
        assertTrue(CropDiagnosis.isUnsure("  "));
    }

    @Test
    void fixedMessages_nameNoDiseaseOrMedicine_andCarryKvkLine() {
        for (String m : new String[]{CropDiagnosis.UNSURE_TAMIL, CropDiagnosis.UNSURE_ENGLISH,
                CropDiagnosis.UNAVAILABLE_TAMIL, CropDiagnosis.UNAVAILABLE_ENGLISH}) {
            String lower = m.toLowerCase();
            assertTrue(m.contains("KVK"), "KVK line missing");
            for (String bad : new String[]{"blast", "tricyclazole", "fungicide", "disease:", "medicine:", "நோய்:", "மருந்து:",
                    "பிளாஸ்ட்", "ட்ரைசைக்கிள்சோல்"}) {
                assertFalse(lower.contains(bad.toLowerCase()), "message must not contain " + bad);
            }
        }
        assertSame(CropDiagnosis.UNSURE_ENGLISH, CropDiagnosis.unsureMessage("english"));
        assertSame(CropDiagnosis.UNSURE_TAMIL, CropDiagnosis.unsureMessage("tamil"));
        assertSame(CropDiagnosis.UNAVAILABLE_ENGLISH, CropDiagnosis.unavailableMessage("english"));
    }

    @Test
    void withKvkNote_appendsNoteInTheRightLanguage() {
        assertTrue(CropDiagnosis.withKvkNote(CONFIDENT_EN, "english").endsWith(CropDiagnosis.KVK_ENGLISH));
        assertTrue(CropDiagnosis.withKvkNote(CONFIDENT_TA, "tamil").endsWith(CropDiagnosis.KVK_TAMIL));
    }
}
