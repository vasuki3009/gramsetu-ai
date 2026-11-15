package com.gramsetu.service;

import com.gramsetu.dto.CropResponse;
import com.gramsetu.repository.CropAdvisoryRepository;
import com.gramsetu.util.CropDiagnosis;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CropVisionServiceTest {

    private static final byte[] IMG = {1, 2, 3, 4};

    private final AIAdvisorService advisor = mock(AIAdvisorService.class);
    private final CropAdvisoryRepository repo = mock(CropAdvisoryRepository.class);
    private final SarvamSpeechService sarvam = mock(SarvamSpeechService.class);

    private CropVisionService service(boolean keyConfigured) {
        when(advisor.isKeyConfigured()).thenReturn(keyConfigured);
        CropVisionService s = new CropVisionService();
        ReflectionTestUtils.setField(s, "advisoryRepo", repo);
        ReflectionTestUtils.setField(s, "sarvamService", sarvam);
        ReflectionTestUtils.setField(s, "aiAdvisor", advisor);
        return s;
    }

    private void geminiReplies(String text) {
        when(advisor.generateFromImage(anyString(), anyString(), anyString())).thenReturn(Optional.of(text));
    }

    private static void assertNoDiseaseOrMedicine(String text) {
        String lower = text.toLowerCase();
        for (String bad : new String[]{"blast", "magnaporthe", "tricyclazole", "பிளாஸ்ட்", "ட்ரைசைக்கிள்சோல்"}) {
            assertFalse(lower.contains(bad.toLowerCase()), "must not contain " + bad);
        }
    }

    @Test
    void noApiKey_returnsUnavailableMessage_notAFakeBlastDiagnosis() {
        CropResponse r = service(false).analyzeCropImage(IMG, "english");
        assertTrue(r.isUnsure());
        assertFalse(r.isVerified());
        assertEquals(CropDiagnosis.UNAVAILABLE_ENGLISH, r.getDiagnosis());
        assertNoDiseaseOrMedicine(r.getDiagnosis());
        verify(advisor, never()).generateFromImage(anyString(), anyString(), anyString());
    }

    @Test
    void everyModelFails_returnsUnavailableMessage_inTamil() {
        when(advisor.generateFromImage(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CropResponse r = service(true).analyzeCropImage(IMG, "tamil");
        assertTrue(r.isUnsure());
        assertEquals(CropDiagnosis.UNAVAILABLE_TAMIL, r.getDiagnosis());
        assertNoDiseaseOrMedicine(r.getDiagnosis());
    }

    @Test
    void unexpectedException_returnsUnavailableMessage() {
        when(advisor.generateFromImage(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("boom"));
        CropResponse r = service(true).analyzeCropImage(IMG, "english");
        assertTrue(r.isUnsure());
        assertEquals(CropDiagnosis.UNAVAILABLE_ENGLISH, r.getDiagnosis());
    }

    @Test
    void sendsJpegBase64OfTheImage_toTheSharedChain() {
        geminiReplies("Crop: Tomato\nConfidence: high\nDisease: Early blight");
        service(true).analyzeCropImage(IMG, "english");
        verify(advisor).generateFromImage(anyString(), eq("image/jpeg"),
                eq(java.util.Base64.getEncoder().encodeToString(IMG)));
    }

    @Test
    void lowConfidenceAnswer_isReplacedByNotSureMessage_noAdvisoryLookup_noAudio() {
        geminiReplies("Crop: unclear\nConfidence: low\nDisease: Blast Disease\nMedicine: Tricyclazole");
        CropResponse r = service(true).analyzeCropImage(IMG, "tamil");
        assertTrue(r.isUnsure());
        assertFalse(r.isVerified());
        assertEquals(CropDiagnosis.UNSURE_TAMIL, r.getDiagnosis());
        assertNoDiseaseOrMedicine(r.getDiagnosis());
        verifyNoInteractions(repo, sarvam);
    }

    @Test
    void confidentAnswer_isReturned_withKvkNote() {
        geminiReplies("Crop: Tomato\nConfidence: high\nDisease: Early blight\nMedicine: Mancozeb");
        CropResponse r = service(true).analyzeCropImage(IMG, "english");
        assertFalse(r.isUnsure());
        assertTrue(r.getDiagnosis().contains("Early blight"));
        assertTrue(r.getDiagnosis().endsWith(CropDiagnosis.KVK_ENGLISH));
    }

    @Test
    void confidentAnswer_unverifiedWhenNoAdvisoryMatches() {
        geminiReplies("Crop: Tomato\nConfidence: medium\nDisease: Early blight\nMedicine: Mancozeb");
        when(repo.findByCropTypeIgnoreCase(anyString())).thenReturn(java.util.List.of());
        CropResponse r = service(true).analyzeCropImage(IMG, "english");
        assertFalse(r.isVerified());
        Mockito.verify(repo, atLeastOnce()).findByCropTypeIgnoreCase(anyString());
    }

    private static com.gramsetu.model.CropAdvisory adv(String disease, String solution, String crop) {
        com.gramsetu.model.CropAdvisory a = new com.gramsetu.model.CropAdvisory();
        a.setDiseaseName(disease);
        a.setVerifiedSolution(solution);
        a.setCropType(crop);
        a.setSource("TNAU 2023");
        return a;
    }

    /** Repo stub holding the real seed rows, looked up by crop like the real query. */
    private void seedRepo() {
        var tomato = adv("Tomato Leaf Curl Virus", "Spray neem oil 5ml/L. Apply imidacloprid 0.5ml/L.", "Tomato");
        var groundnut = adv("Groundnut Leaf Spot", "Apply mancozeb 2.5g/L.", "Groundnut");
        when(repo.findByCropTypeIgnoreCase(anyString())).thenAnswer(inv -> {
            String c = inv.getArgument(0);
            if ("tomato".equalsIgnoreCase(c)) return java.util.List.of(tomato);
            if ("groundnut".equalsIgnoreCase(c)) return java.util.List.of(groundnut);
            return java.util.List.of();
        });
    }

    @Test
    void mulberryLeafSpot_isNotVerified_andKeepsModelMedicine() {
        seedRepo();
        geminiReplies("Crop: Mulberry\nConfidence: high\nDisease: Leaf Spot / Cercospora leaf spot\n"
                + "Medicine: Carbendazim");
        CropResponse r = service(true).analyzeCropImage(IMG, "english");
        assertFalse(r.isVerified());
        assertEquals("AI பரிந்துரை", r.getVerifiedSource());
        assertTrue(r.getDiagnosis().contains("Medicine: Carbendazim"));
        assertFalse(r.getDiagnosis().contains(CropDiagnosis.VERIFIED_LABEL_ENGLISH));
    }

    @Test
    void tomatoLeafCurl_isVerified_modelMedicineRemoved_advisorySolutionAppended() {
        seedRepo();
        geminiReplies("Crop: Tomato\nConfidence: high\nDisease: Tomato Leaf Curl Virus\n"
                + "Treatment:\n1. Remove leaves\nMedicine: Acephate\n");
        CropResponse r = service(true).analyzeCropImage(IMG, "english");
        assertTrue(r.isVerified());
        assertEquals("TNAU 2023", r.getVerifiedSource());
        assertFalse(r.getDiagnosis().contains("Acephate"));
        assertFalse(r.getDiagnosis().contains("Medicine:"));
        assertTrue(r.getDiagnosis().contains(CropDiagnosis.VERIFIED_LABEL_ENGLISH
                + " Spray neem oil 5ml/L. Apply imidacloprid 0.5ml/L."));
        assertTrue(r.getDiagnosis().endsWith(CropDiagnosis.KVK_ENGLISH));
    }

    @Test
    void tamilFormat_withTamilCropAlias_isVerified_andMedicineLineRemoved() {
        seedRepo();
        geminiReplies("பயிர்: தக்காளி\nநம்பிக்கை: high\nநோய்: தக்காளி இலை சுருட்டை Tomato Leaf Curl Virus\n"
                + "மருந்து: அசிபேட்");
        CropResponse r = service(true).analyzeCropImage(IMG, "tamil");
        assertTrue(r.isVerified());
        assertFalse(r.getDiagnosis().contains("அசிபேட்"));
        assertTrue(r.getDiagnosis().contains(CropDiagnosis.VERIFIED_LABEL_TAMIL + " Spray neem oil"));
    }

    @Test
    void cropMatchesButDiseaseDiffers_isNotVerified() {
        seedRepo();
        geminiReplies("Crop: Tomato\nConfidence: high\nDisease: Early blight\nMedicine: Mancozeb");
        CropResponse r = service(true).analyzeCropImage(IMG, "english");
        assertFalse(r.isVerified());
        assertTrue(r.getDiagnosis().contains("Medicine: Mancozeb"));
    }

    @Test
    void diseaseMatchesButCropDiffers_isNotVerified() {
        seedRepo();
        // Disease text equals the Groundnut advisory, but the model says the crop is Mulberry.
        geminiReplies("Crop: Mulberry\nConfidence: high\nDisease: Groundnut Leaf Spot\nMedicine: Carbendazim");
        CropResponse r = service(true).analyzeCropImage(IMG, "english");
        assertFalse(r.isVerified());
        assertTrue(r.getDiagnosis().contains("Medicine: Carbendazim"));
    }

    @Test
    void groundnutLeafSpot_isVerified() {
        seedRepo();
        geminiReplies("Crop: Groundnut\nConfidence: medium\nDisease: Leaf Spot (Cercospora)\nMedicine: X");
        CropResponse r = service(true).analyzeCropImage(IMG, "english");
        assertTrue(r.isVerified());
        assertTrue(r.getDiagnosis().contains("Apply mancozeb 2.5g/L."));
        assertFalse(r.getDiagnosis().contains("Medicine: X"));
    }
}
