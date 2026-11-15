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

    // ---- reason codes ----

    @Test
    void unsureReason_eachCode() {
        assertNull(CropDiagnosis.unsureReason(CONFIDENT_EN));
        assertNull(CropDiagnosis.unsureReason(CONFIDENT_TA));
        assertEquals(CropDiagnosis.UnsureReason.EMPTY_ANSWER, CropDiagnosis.unsureReason(null));
        assertEquals(CropDiagnosis.UnsureReason.EMPTY_ANSWER, CropDiagnosis.unsureReason("  "));
        assertEquals(CropDiagnosis.UnsureReason.NO_CONFIDENCE_LINE,
                CropDiagnosis.unsureReason(CONFIDENT_EN.replace("Confidence: high\n", "")));
        assertEquals(CropDiagnosis.UnsureReason.NO_CONFIDENCE_LINE,
                CropDiagnosis.unsureReason(CONFIDENT_EN.replace("high", "very sure")));
        assertEquals(CropDiagnosis.UnsureReason.LOW_CONFIDENCE,
                CropDiagnosis.unsureReason(CONFIDENT_EN.replace("high", "low")));
        assertEquals(CropDiagnosis.UnsureReason.NO_CROP,
                CropDiagnosis.unsureReason(CONFIDENT_EN.replace("Crop: Tomato\n", "")));
        assertEquals(CropDiagnosis.UnsureReason.UNCLEAR_CROP,
                CropDiagnosis.unsureReason(CONFIDENT_EN.replace("Tomato", "not a crop leaf")));
        assertEquals(CropDiagnosis.UnsureReason.NOT_SURE_PHRASE,
                CropDiagnosis.unsureReason(CONFIDENT_EN + "Result: I am not sure."));
        assertEquals(CropDiagnosis.UnsureReason.NO_DISEASE_SECTION,
                CropDiagnosis.unsureReason("Crop: Tomato\nConfidence: high\nSymptoms: spots"));
    }

    // ---- tentative reply ----

    private static final String LOW_WITH_GUESS_EN =
            "Crop: Tomato\nConfidence: low\nSymptoms: many small dark spots on a green leaf\n"
            + "Disease: Early blight";

    @Test
    void tentative_english_hasGuessSymptomsProblem_andNoMedicineOrVerifiedLine() {
        String m = CropDiagnosis.tentativeMessage(LOW_WITH_GUESS_EN, "english");
        assertNotNull(m);
        assertTrue(m.contains("Tomato"));
        assertTrue(m.contains("many small dark spots on a green leaf"));
        assertTrue(m.contains("Early blight"));
        assertTrue(m.contains("not confirmed"));
        assertTrue(m.contains("close, well-lit photo"));
        assertTrue(m.endsWith(CropDiagnosis.KVK_ENGLISH));
        assertNoMedicineOrVerified(m);
    }

    @Test
    void tentative_tamil_usesTamilTemplate() {
        String m = CropDiagnosis.tentativeMessage(
                "பயிர்: தக்காளி\nநம்பிக்கை: low\nஅறிகுறிகள்: பச்சை இலையில் நிறைய கருப்புப் புள்ளிகள்\nநோய்: இலைப்புள்ளி நோய்",
                "tamil");
        assertNotNull(m);
        assertTrue(m.startsWith("எனக்கு முழுமையாக உறுதியாகத் தெரியவில்லை."));
        assertTrue(m.contains("தக்காளி"));
        assertTrue(m.contains("பச்சை இலையில் நிறைய கருப்புப் புள்ளிகள்"));
        assertTrue(m.contains("இலைப்புள்ளி நோய்"));
        assertTrue(m.endsWith(CropDiagnosis.KVK_TAMIL));
        assertNoMedicineOrVerified(m);
    }

    @Test
    void tentative_neverCarriesMedicineOrVerifiedLine_evenIfModelSneaksOneIn() {
        String sneaky = LOW_WITH_GUESS_EN
                + "\nTreatment:\n1. Spray Mancozeb 2.5 g/L\nMedicine: Mancozeb\n"
                + CropDiagnosis.VERIFIED_LABEL_ENGLISH + " Apply carbendazim";
        String m = CropDiagnosis.tentativeMessage(sneaky, "english");
        assertNotNull(m);
        assertFalse(m.toLowerCase().contains("mancozeb"));
        assertFalse(m.toLowerCase().contains("carbendazim"));
        assertNoMedicineOrVerified(m);

        String sneakyTamil = "பயிர்: தக்காளி\nநம்பிக்கை: low\nஅறிகுறிகள்: கருப்புப் புள்ளிகள்\nநோய்: இலைப்புள்ளி\n"
                + "மருந்து: மான்கோசெப்\n" + CropDiagnosis.VERIFIED_LABEL_TAMIL + " தெளிக்கவும்";
        String mt = CropDiagnosis.tentativeMessage(sneakyTamil, "tamil");
        assertNotNull(mt);
        assertFalse(mt.contains("மான்கோசெப்"));
        assertNoMedicineOrVerified(mt);
    }

    @Test
    void tentative_rejectsMedicineInsideTheNamedFields() {
        assertNull(CropDiagnosis.tentativeMessage(
                LOW_WITH_GUESS_EN.replace("Early blight", "Early blight - spray mancozeb"), "english"));
        assertNull(CropDiagnosis.tentativeMessage(
                LOW_WITH_GUESS_EN.replace("Early blight", "Early blight 2 g/L"), "english"));
        assertNull(CropDiagnosis.tentativeMessage(
                LOW_WITH_GUESS_EN.replace("dark spots", "dark spots, apply fungicide"), "english"));
    }

    @Test
    void tentative_notOffered_whenNotLowOrSectionsMissingOrNotAPlant() {
        // medium / high: the normal diagnosis path, not tentative
        assertNull(CropDiagnosis.tentativeMessage(LOW_WITH_GUESS_EN.replace("low", "medium"), "english"));
        assertNull(CropDiagnosis.tentativeMessage(CONFIDENT_EN, "english"));
        // missing symptoms / disease / crop
        assertNull(CropDiagnosis.tentativeMessage("Crop: Tomato\nConfidence: low\nDisease: Early blight", "english"));
        assertNull(CropDiagnosis.tentativeMessage("Crop: Tomato\nConfidence: low\nSymptoms: dark spots", "english"));
        assertNull(CropDiagnosis.tentativeMessage("Confidence: low\nSymptoms: dark spots\nDisease: Early blight", "english"));
        // not a plant / unclear crop / the model's own "not sure" escape
        assertNull(CropDiagnosis.tentativeMessage(LOW_WITH_GUESS_EN.replace("Tomato", "not a crop leaf"), "english"));
        assertNull(CropDiagnosis.tentativeMessage("Crop: unclear\nConfidence: low\nResult: I am not sure.", "english"));
        assertNull(CropDiagnosis.tentativeMessage(LOW_WITH_GUESS_EN + "\nResult: I am not sure.", "english"));
        // unknown disease
        assertNull(CropDiagnosis.tentativeMessage(LOW_WITH_GUESS_EN.replace("Early blight", "unknown"), "english"));
        assertNull(CropDiagnosis.tentativeMessage(null, "english"));
    }

    private static void assertNoMedicineOrVerified(String m) {
        String lower = m.toLowerCase();
        assertFalse(lower.contains("medicine:"));
        assertFalse(m.contains("மருந்து:"));
        assertFalse(m.contains(CropDiagnosis.VERIFIED_LABEL_ENGLISH));
        assertFalse(m.contains(CropDiagnosis.VERIFIED_LABEL_TAMIL));
        assertFalse(lower.contains("treatment:"));
        assertFalse(m.contains("சிகிச்சை:"));
    }

    @Test
    void withKvkNote_appendsNoteInTheRightLanguage() {
        assertTrue(CropDiagnosis.withKvkNote(CONFIDENT_EN, "english").endsWith(CropDiagnosis.KVK_ENGLISH));
        assertTrue(CropDiagnosis.withKvkNote(CONFIDENT_TA, "tamil").endsWith(CropDiagnosis.KVK_TAMIL));
    }
}
