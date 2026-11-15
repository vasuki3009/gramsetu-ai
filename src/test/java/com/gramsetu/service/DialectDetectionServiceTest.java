package com.gramsetu.service;

import com.gramsetu.dto.DialectResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DialectDetectionServiceTest {

    private DialectDetectionService service;

    @BeforeEach
    void setUp() {
        service = new DialectDetectionService();   // default dialect: standard
    }

    private DialectResult detect(String text) {
        return service.detect(text, "tamil", "");
    }

    @Test
    @DisplayName("Clearly Chennai-style message is detected as Chennai (it used to score Kongu)")
    void chennaiExample() {
        DialectResult r = detect("என் தக்காளி செடி இலை எல்லாம் சுருண்டு கீது, என்ன பண்றது?");
        assertEquals("CHENNAI", r.getDialect());
        assertEquals("detected", r.getDetectedBy());
    }

    @Test
    @DisplayName("Strong markers of each dialect are detected")
    void eachDialectDetected() {
        assertEquals("CHENNAI", detect("மச்சி என் பயிர் ஜாஸ்தி வாடிடுச்சு").getDialect());
        assertEquals("MADURAI", detect("அம்புட்டு தண்ணி விட்டும் பயிர் வாடுது கம்மாய் வத்திடுச்சு").getDialect());
        assertEquals("NELLAI", detect("அண்ணாச்சி நெல்லுக்கு என்ன போடணும்").getDialect());
        assertEquals("KONGU", detect("ஏனுங்க செடி வாடிப் போகுது").getDialect());
    }

    @Test
    @DisplayName("No marker at all → the default (standard Tamil), not Kongu")
    void noMarkerFallsBackToStandard() {
        DialectResult r = detect("நெல் பயிருக்கு எவ்வளவு தண்ணீர் விட வேண்டும்?");
        assertEquals("STANDARD", r.getDialect());
        assertEquals("default", r.getDetectedBy());
    }

    @Test
    @DisplayName("\"செத்தது\" (died) must not score as strong Kongu through the weak marker \"செத்த\"")
    void diedIsNotKongu() {
        assertEquals(1, DialectDetectionService.score("பயிர் செத்தது").get("KONGU"));   // weak, 1 point
        DialectResult r = detect("பயிர் செத்தது");
        assertEquals("STANDARD", r.getDialect());
        assertEquals("default", r.getDetectedBy());
        assertEquals(DialectDetectionService.WEAK, weightOf("KONGU", "செத்த"));
        assertEquals(DialectDetectionService.WEAK, weightOf("CHENNAI", "மச்சான்"));
    }

    @Test
    @DisplayName("One word is counted once even when a shorter marker sits inside a longer one")
    void nestedMarkersCountOnce() {
        assertEquals(2, DialectDetectionService.score("இன்னாப்பா").get("CHENNAI"));   // not 4 (இன்னா + இன்னாப்பா)
        assertEquals(1, DialectDetectionService.score("வேணும்").get("KONGU"));        // not 2 (வேணும + வேணும்)
        assertEquals("STANDARD", detect("உரம் வேணும்").getDialect());      // one weak word never decides
    }

    private static int weightOf(String dialect, String word) {
        return DialectDetectionService.MARKERS.get(dialect).stream()
                .filter(m -> m.word().equals(word)).findFirst().orElseThrow().weight();
    }

    @Test
    @DisplayName("Short or unclear messages fall back to standard")
    void shortMessages() {
        assertEquals("STANDARD", detect("தக்காளி").getDialect());
        assertEquals("STANDARD", detect("?").getDialect());
        assertEquals("STANDARD", detect("   ").getDialect());
        assertEquals("STANDARD", detect(null).getDialect());
    }

    @Test
    @DisplayName("A single weak Kongu match does not decide the dialect")
    void singleWeakMatchIgnored() {
        DialectResult r = detect("எங்க தோட்டத்தில் இலை மஞ்சளாக இருக்கிறது");   // "எங்க" is a weak Kongu marker
        assertEquals("STANDARD", r.getDialect());
        assertEquals("default", r.getDetectedBy());
    }

    @Test
    @DisplayName("A weak Kongu match never beats strong evidence for another dialect")
    void weakDoesNotBeatStrong() {
        // "இருக்கு" = Kongu 1, "கீது" = Chennai 2
        assertEquals("CHENNAI", detect("இலை எல்லாம் சுருண்டு இருக்கு கீது").getDialect());
    }

    @Test
    @DisplayName("A tie at the top gives the default, not a favourite")
    void tieGivesDefault() {
        // "அம்புட்டு" Madurai 2, "ஏலே" Nellai 2
        DialectResult r = detect("ஏலே அம்புட்டு");
        assertEquals("STANDARD", r.getDialect());
        assertEquals("default", r.getDetectedBy());
        // weak ties as well: Kongu 2 (இருக்கு, தண்ணி) vs Chennai 2 (கீது)
        assertEquals("STANDARD", detect("இருக்கு தண்ணி கீது").getDialect());
    }

    @Test
    @DisplayName("DEFAULT_DIALECT is configurable; unknown values mean standard")
    void configurableDefault() {
        service.setDefaultDialect("kongu");
        DialectResult r = detect("நெல் பயிருக்கு எவ்வளவு தண்ணீர்?");
        assertEquals("KONGU", r.getDialect());
        assertEquals("default", r.getDetectedBy());

        service.setDefaultDialect(" Chennai ");
        assertEquals("CHENNAI", detect("நெல் பயிருக்கு எவ்வளவு தண்ணீர்?").getDialect());

        service.setDefaultDialect("klingon");
        assertEquals("STANDARD", detect("நெல் பயிருக்கு எவ்வளவு தண்ணீர்?").getDialect());
        service.setDefaultDialect(null);
        assertEquals("STANDARD", detect("நெல் பயிருக்கு எவ்வளவு தண்ணீர்?").getDialect());
    }

    @Test
    @DisplayName("A detected dialect beats the configured default")
    void detectionBeatsDefault() {
        service.setDefaultDialect("kongu");
        assertEquals("CHENNAI", detect("இலை எல்லாம் சுருண்டு கீது, என்ன பண்றது?").getDialect());
    }

    @Test
    @DisplayName("The English answer prompt asks for English only; the Tamil prompts are in Tamil")
    void answerPromptLanguage() {
        String english = service.buildAnswerPrompt("How do I join PM Kisan?", DialectResult.standard(), "english");
        assertTrue(english.contains("Answer in English only"), english);
        assertFalse(com.gramsetu.util.AnswerLanguage.hasTamil(english));
        assertTrue(com.gramsetu.util.AnswerLanguage.hasTamil(
                service.buildAnswerPrompt("நெல்", DialectResult.standard(), "tamil")));
    }

    @Test
    @DisplayName("English questions are always standard")
    void englishIsStandard() {
        assertEquals("STANDARD", service.detect("கீது கீறேன்", "english", "").getDialect());
    }

    @Test
    @DisplayName("Marker lists: four dialects, no empty or duplicate entries, the new Chennai words are present")
    void markerLists() {
        assertEquals(Set.of("KONGU", "MADURAI", "NELLAI", "CHENNAI"), DialectDetectionService.MARKERS.keySet());
        for (var e : DialectDetectionService.MARKERS.entrySet()) {
            Set<String> seen = new HashSet<>();
            for (var m : e.getValue()) {
                assertFalse(m.word().isBlank(), e.getKey());
                assertTrue(seen.add(m.word()), "duplicate in " + e.getKey() + ": " + m.word());
            }
        }
        Set<String> chennai = new HashSet<>();
        DialectDetectionService.MARKERS.get("CHENNAI").forEach(m -> chennai.add(m.word()));
        assertTrue(chennai.containsAll(List.of("கீது", "கீறேன்", "பண்றது", "ஜாஸ்தி", "இன்னாப்பா", "நைனா", "மச்சி", "அப்டி", "இப்டி")));
    }

    // ── Manual override ──

    @Test
    @DisplayName("Override commands: English and Tamil forms")
    void overrideCommands() {
        assertEquals(Optional.of("CHENNAI"), DialectDetectionService.parseOverrideCommand("dialect chennai"));
        assertEquals(Optional.of("MADURAI"), DialectDetectionService.parseOverrideCommand("Dialect Madurai"));
        assertEquals(Optional.of("NELLAI"), DialectDetectionService.parseOverrideCommand("  dialect: nellai  "));
        assertEquals(Optional.of("KONGU"), DialectDetectionService.parseOverrideCommand("DIALECT KONGU!"));
        assertEquals(Optional.of("STANDARD"), DialectDetectionService.parseOverrideCommand("dialect standard"));
        assertEquals(Optional.of("CHENNAI"), DialectDetectionService.parseOverrideCommand("சென்னை தமிழ்"));
        assertEquals(Optional.of("MADURAI"), DialectDetectionService.parseOverrideCommand("மதுரை  தமிழ்"));
        assertEquals(Optional.of("NELLAI"), DialectDetectionService.parseOverrideCommand("நெல்லை தமிழ்"));
        assertEquals(Optional.of("KONGU"), DialectDetectionService.parseOverrideCommand("கொங்கு தமிழ்."));
    }

    @Test
    @DisplayName("Ordinary messages are not override commands")
    void notOverrideCommands() {
        assertTrue(DialectDetectionService.parseOverrideCommand("dialect").isEmpty());
        assertTrue(DialectDetectionService.parseOverrideCommand("dialect tamil").isEmpty());
        assertTrue(DialectDetectionService.parseOverrideCommand("my dialect chennai please").isEmpty());
        assertTrue(DialectDetectionService.parseOverrideCommand("சென்னை").isEmpty());
        assertTrue(DialectDetectionService.parseOverrideCommand("சென்னை தமிழ் பற்றி சொல்லுங்கள்").isEmpty());
        assertTrue(DialectDetectionService.parseOverrideCommand("").isEmpty());
        assertTrue(DialectDetectionService.parseOverrideCommand(null).isEmpty());
    }

    @Test
    @DisplayName("Override result and Tamil confirmation")
    void overrideResultAndConfirmation() {
        DialectResult r = DialectDetectionService.overrideResult("MADURAI");
        assertEquals("MADURAI", r.getDialect());
        assertEquals("override", r.getDetectedBy());
        assertTrue(DialectDetectionService.overrideConfirmation("CHENNAI").contains("சென்னை தமிழில்"));
        assertTrue(DialectDetectionService.overrideConfirmation("STANDARD").contains("எளிய தமிழில்"));
    }
}
