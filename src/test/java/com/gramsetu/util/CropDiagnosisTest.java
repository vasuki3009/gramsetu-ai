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

    // ---- tolerant parsing: formats a smaller model may produce ----

    private static void assertShown(String reply) {
        assertNull(CropDiagnosis.unsureReason(reply), "should be a normal diagnosis: " + reply);
        assertEquals("Tomato", CropDiagnosis.crop(reply));
    }

    @Test
    void format_bold() {
        assertShown("**Crop:** Tomato\n**Confidence:** High\n**Disease:** Early blight");
        assertShown("**Crop**: Tomato\n**Confidence**: **High**\n**Disease**: Early blight");
        assertShown("**Crop: Tomato**\n**Confidence: high**\n**Disease: Early blight**");
        assertShown("__Crop:__ Tomato\n_Confidence_: high\nDisease: Early blight");
    }

    @Test
    void format_numbered() {
        assertShown("1. Crop: Tomato\n2. Confidence: high\n3. Disease: Early blight");
        assertShown("1) **Crop:** Tomato\n2) **Confidence:** medium\n3) **Disease:** Early blight");
    }

    @Test
    void format_bulletsHeadingsAndQuotes() {
        assertShown("- Crop: Tomato\n- Confidence: high\n- Disease: Early blight");
        assertShown("• Crop: Tomato\n• Confidence: high\n• Disease: Early blight");
        assertShown("* Crop: Tomato\n* Confidence: high\n* Disease: Early blight");
        assertShown("## Crop: Tomato\n## Confidence: high\n## Disease: Early blight");
        assertShown("> Crop: Tomato\n> Confidence: high\n> Disease: Early blight");
    }

    @Test
    void format_labelSynonyms() {
        assertShown("Plant: Tomato\nConfidence level: High\nProblem: Early blight");
        assertShown("Crop Name: Tomato\nConfidence: high\nIssue: Early blight");
        assertShown("Crop identified: Tomato\nConfidence: high\nPossible problem: Early blight");
        assertEquals("Early blight", CropDiagnosis.disease("Problem: Early blight"));
    }

    @Test
    void format_caseSpacingAndFullWidthColon() {
        assertShown("CROP : Tomato\ncONFIDENCE:high\ndisease   :   Early blight");
        assertShown("Crop： Tomato\nConfidence：high\nDisease： Early blight");
        assertShown("Crop (பயிர்): Tomato\nConfidence: high\nDisease: Early blight");
    }

    @Test
    void format_tamilLabelsAndTamilConfidenceWords() {
        assertNull(CropDiagnosis.unsureReason("பயிர்: தக்காளி\nநம்பிக்கை: அதிகம்\nநோய்: இலை கருகல் நோய்"));
        assertNull(CropDiagnosis.unsureReason("**பயிர்:** தக்காளி\n**நம்பிக்கை:** நடுத்தரம்\n**நோய்:** இலை கருகல்"));
        assertNull(CropDiagnosis.unsureReason("1. பயிரின் பெயர்: தக்காளி\n2. நம்பிக்கை: medium\n3. நோய்: இலை கருகல்"));
        assertEquals("தக்காளி", CropDiagnosis.crop("பயிர் : தக்காளி"));
        assertEquals("low", CropDiagnosis.confidence("நம்பிக்கை: குறைவு"));
        assertEquals(CropDiagnosis.UnsureReason.LOW_CONFIDENCE,
                CropDiagnosis.unsureReason("பயிர்: தக்காளி\nநம்பிக்கை: குறைவு\nநோய்: இலை கருகல்"));
    }

    @Test
    void format_missingConfidence_isStillUnsure() {
        assertEquals(CropDiagnosis.UnsureReason.NO_CONFIDENCE_LINE,
                CropDiagnosis.unsureReason("1. **Crop:** Tomato\n2. **Disease:** Early blight"));
        assertEquals(CropDiagnosis.UnsureReason.NO_CONFIDENCE_LINE,
                CropDiagnosis.unsureReason("Crop: Tomato\nConfidence: 85%\nDisease: Early blight"));
    }

    @Test
    void format_lowConfidenceTentative_worksWithNewFormats() {
        String m = CropDiagnosis.tentativeMessage(
                "1. **Plant:** Tomato\n2. **Confidence level:** Low\n3. **Symptoms:** small dark spots\n4. **Problem:** Early blight",
                "english");
        assertNotNull(m);
        assertTrue(m.contains("Tomato") && m.contains("small dark spots") && m.contains("Early blight"));
        assertNoMedicineOrVerified(m);
    }

    // ---- tolerant parsing must not weaken the safety rules ----

    @Test
    void safety_similarLabelsAreNotTheCropOrDisease() {
        assertNull(CropDiagnosis.crop("Crop rotation: paddy\nConfidence: high"));
        assertNull(CropDiagnosis.crop("Plantation: 2 acres"));
        assertNull(CropDiagnosis.disease("Disease management: spray"));
        assertEquals(CropDiagnosis.UnsureReason.NO_CROP,
                CropDiagnosis.unsureReason("Crop rotation: paddy\nConfidence: high\nDisease: Early blight"));
    }

    @Test
    void safety_synonymStillGoesThroughUnclearAndNotSureChecks() {
        assertEquals(CropDiagnosis.UnsureReason.UNCLEAR_CROP,
                CropDiagnosis.unsureReason("Plant: not a crop leaf\nConfidence: high\nProblem: Early blight"));
        assertEquals(CropDiagnosis.UnsureReason.NOT_SURE_PHRASE,
                CropDiagnosis.unsureReason("1. Plant: Tomato\n2. Confidence: high\n3. Problem: Early blight\nResult: I am not sure."));
    }

    @Test
    void safety_lowConfidenceNeverGetsMedicine_inAnyFormat() {
        String low = "1. **Crop:** Tomato\n2. **Confidence:** low\n3. **Symptoms:** dark spots\n4. **Disease:** Early blight\n"
                + "5. **Medicine:** Mancozeb";
        assertEquals(CropDiagnosis.UnsureReason.LOW_CONFIDENCE, CropDiagnosis.unsureReason(low));
        String m = CropDiagnosis.tentativeMessage(low, "english");
        assertNotNull(m);
        assertFalse(m.toLowerCase().contains("mancozeb"));
        assertNull(CropDiagnosis.tentativeMessage(low.replace("dark spots", "dark spots, spray mancozeb"), "english"));
    }

    @Test
    void safety_modelsOwnMedicineLineIsDroppedInAnyFormat() {
        for (String medicine : new String[]{"Medicine: Mancozeb", "**Medicine:** Mancozeb", "- Medicine: Mancozeb",
                "5. Medicine: Mancozeb", "5. **Medicine:** Mancozeb", "## Medicine: Mancozeb", "Medicine： Mancozeb"}) {
            String out = CropDiagnosis.withVerifiedSolution("Crop: Tomato\n" + medicine + "\nNote: x", "Use Y", "english");
            assertFalse(out.toLowerCase().contains("mancozeb"), "not dropped: " + medicine);
            assertTrue(out.contains(CropDiagnosis.VERIFIED_LABEL_ENGLISH + " Use Y"));
        }
    }

    // ---- value on the next line: only short, non-label, non-bullet lines ----

    private static String nextLineReply(String cropValueLine) {
        return "Confidence: high\nCrop:\n" + cropValueLine + "\nDisease: Early blight";
    }

    @Test
    void nextLine_shortPlainValue_isAccepted() {
        assertShown(nextLineReply("Tomato"));
        assertShown(nextLineReply("**Tomato**"));
        assertShown("Confidence: high\nCrop:\n\n  Tomato  \nDisease: Early blight");   // blank line in between
        assertShown("**Crop:**\nTomato\n**Confidence:** high\n**Disease:** Early blight");
    }

    @Test
    void nextLine_lengthLimitIs40Characters() {
        String forty = "a".repeat(40);
        String fortyOne = "a".repeat(41);
        assertEquals(forty, CropDiagnosis.crop(nextLineReply(forty)));
        assertNull(CropDiagnosis.unsureReason(nextLineReply(forty)));
        assertEquals("", CropDiagnosis.crop(nextLineReply(fortyOne)));
        assertEquals(CropDiagnosis.UnsureReason.NO_CROP, CropDiagnosis.unsureReason(nextLineReply(fortyOne)));
    }

    @Test
    void nextLine_aLabelLineIsNeverTakenAsTheValue() {
        assertEquals(CropDiagnosis.UnsureReason.NO_CROP,
                CropDiagnosis.unsureReason("Crop:\nDisease: Early blight\nConfidence: high"));
        assertEquals(CropDiagnosis.UnsureReason.NO_CROP,
                CropDiagnosis.unsureReason("Crop:\nConfidence: high\nDisease: Early blight"));
        // a recognised label followed by a colon anywhere in the next line also blocks it
        assertEquals(CropDiagnosis.UnsureReason.NO_CROP,
                CropDiagnosis.unsureReason(nextLineReply("Tomato (disease: blight)")));
        assertEquals(CropDiagnosis.UnsureReason.NO_CROP,
                CropDiagnosis.unsureReason(nextLineReply("**நோய்:** இலை கருகல்")));
        assertEquals("", CropDiagnosis.crop("Crop:\nTreatment: remove leaves\nConfidence: high"));
    }

    @Test
    void nextLine_aBulletOrHeadingStartingANewSectionIsNeverTakenAsTheValue() {
        for (String bullet : new String[]{"- Tomato", "• Tomato", "* Tomato", "1. Tomato", "2) Tomato", "## Tomato"}) {
            assertEquals(CropDiagnosis.UnsureReason.NO_CROP, CropDiagnosis.unsureReason(nextLineReply(bullet)),
                    "bullet accepted as value: " + bullet);
        }
    }

    @Test
    void nextLine_endOfTextOrOnlyBlankLines_givesEmptyValue() {
        assertEquals("", CropDiagnosis.crop("Confidence: high\nCrop:"));
        assertEquals("", CropDiagnosis.crop("Confidence: high\nCrop:\n\n   \n"));
    }

    // ---- labelsFound: label names only, never values ----

    @Test
    void labelsFound_returnsOnlyLabelNames() {
        String reply = "1. **Plant Name:** Tomato\n- Confidence: high\n"
                + "The tomato leaf shows many dark brown spots with rings: early blight\n"
                + "Step 2: do x\nDisease: Early blight (Alternaria)\nNo colon here";
        java.util.List<String> labels = CropDiagnosis.labelsFound(reply);
        assertEquals(java.util.List.of("Plant Name", "Confidence", "Disease"), labels);
        String joined = String.join("|", labels).toLowerCase();
        for (String value : new String[]{"tomato", "high", "blight", "alternaria", "spots"}) {
            assertFalse(joined.contains(value), "value leaked: " + value);
        }
    }

    @Test
    void labelsFound_capsCountAndLength() {
        StringBuilder many = new StringBuilder();
        for (char c = 'a'; c <= 'o'; c++) many.append("Label ").append(c).append(": v\n");
        assertEquals(10, CropDiagnosis.labelsFound(many.toString()).size());
        for (String label : CropDiagnosis.labelsFound("A".repeat(20) + ": v\n" + "A".repeat(21) + ": v")) {
            assertTrue(label.length() <= 20);
        }
        assertEquals(1, CropDiagnosis.labelsFound("A".repeat(20) + ": v\n" + "A".repeat(21) + ": v").size());
    }

    @Test
    void labelsFound_skipsProseDigitsAndHandlesNullOrEmpty() {
        assertTrue(CropDiagnosis.labelsFound(null).isEmpty());
        assertTrue(CropDiagnosis.labelsFound("").isEmpty());
        assertTrue(CropDiagnosis.labelsFound("Tomato early blight on lower old leaves: yes").isEmpty());
        assertTrue(CropDiagnosis.labelsFound("Step 1: spray\nRate 5g: ok").isEmpty());
        assertEquals(java.util.List.of("பயிர்", "நம்பிக்கை"),
                CropDiagnosis.labelsFound("பயிர்: தக்காளி\nநம்பிக்கை: அதிகம்"));
    }

    @Test
    void withKvkNote_appendsNoteInTheRightLanguage() {
        assertTrue(CropDiagnosis.withKvkNote(CONFIDENT_EN, "english").endsWith(CropDiagnosis.KVK_ENGLISH));
        assertTrue(CropDiagnosis.withKvkNote(CONFIDENT_TA, "tamil").endsWith(CropDiagnosis.KVK_TAMIL));
    }
}
