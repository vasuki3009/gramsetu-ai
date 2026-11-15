package com.gramsetu.controller;

import com.gramsetu.dto.DialectResult;
import com.gramsetu.dto.SchemeResponse;
import com.gramsetu.repository.FarmerQueryRepository;
import com.gramsetu.service.AIAdvisorService;
import com.gramsetu.service.DialectDetectionService;
import com.gramsetu.service.SarvamSpeechService;
import com.gramsetu.service.SchemeRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The website's voice endpoint appends the scheme list only to questions about schemes, in the answer's language. */
@WebMvcTest(FarmerController.class)
class VoiceSchemeListTest {

    @Autowired MockMvc mvc;
    @MockBean SarvamSpeechService sarvamService;
    @MockBean AIAdvisorService aiService;
    @MockBean DialectDetectionService dialectService;
    @MockBean FarmerQueryRepository farmerQueryRepo;
    @MockBean SchemeRecommendationService schemeService;

    @BeforeEach
    void stubs() {
        when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
        when(dialectService.buildAnswerPrompt(anyString(), any(), anyString())).thenReturn("prompt");
        when(aiService.getAnswer(anyString(), anyString(), anyString())).thenReturn("THE-ANSWER");
        when(sarvamService.toAudio(anyString(), anyString())).thenReturn("");
        when(schemeService.findMatchingSchemes(anyString(), anyString())).thenReturn(List.of(
                new SchemeResponse("PM Kisan Samman Nidhi", "", "", "", null),
                new SchemeResponse("PMFBY", "", "", "", null)));
    }

    private ResultActions postVoice(String transcript, String language) throws Exception {
        when(sarvamService.toText(any(), anyString())).thenReturn(transcript);
        when(sarvamService.toTextAutoDetect(any(), anyString())).thenReturn(transcript);   // English mode
        MockMultipartFile audio = new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[200]);
        return mvc.perform(multipart("/api/v1/farmer/voice").file(audio).param("language", language))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A greeting gets the plain answer: no scheme list, and the scheme service is not even called")
    void greetingHasNoSchemeList() throws Exception {
        postVoice("ஹலோ", "tamil")
                .andExpect(jsonPath("$.tamilAnswer").value("THE-ANSWER"))
                .andExpect(jsonPath("$.matchingSchemes").isEmpty());
        verify(schemeService, never()).findMatchingSchemes(anyString(), anyString());
    }

    @Test
    @DisplayName("An ordinary farming question gets no scheme list")
    void farmingQuestionHasNoSchemeList() throws Exception {
        postVoice("தக்காளி இலை சுருள்கிறது", "tamil")
                .andExpect(jsonPath("$.tamilAnswer").value("THE-ANSWER"));
        verify(schemeService, never()).findMatchingSchemes(anyString(), anyString());
    }

    @Test
    @DisplayName("A Tamil scheme question gets the list under a Tamil heading")
    void tamilSchemeQuestion() throws Exception {
        postVoice("விவசாயிகளுக்கு என்ன மானிய திட்டங்கள் உள்ளன", "tamil")
                .andExpect(jsonPath("$.tamilAnswer", containsString("பொருத்தமான அரசு திட்டங்கள்:")))
                .andExpect(jsonPath("$.tamilAnswer", containsString("• PM Kisan Samman Nidhi")))
                .andExpect(jsonPath("$.tamilAnswer", not(containsString("Matching Government Schemes"))));
    }

    @Test
    @DisplayName("Only the answer is spoken: the scheme list appended under it is not sent to text-to-speech")
    void schemeListIsNotSpoken() throws Exception {
        postVoice("விவசாயிகளுக்கு என்ன மானிய திட்டங்கள் உள்ளன", "tamil")
                .andExpect(jsonPath("$.tamilAnswer", containsString("• PM Kisan Samman Nidhi")));
        verify(sarvamService).toAudio("THE-ANSWER", "STANDARD");
        verify(sarvamService, never()).toAudio(contains("PM Kisan"), anyString());
    }

    @Test
    @DisplayName("English mode but a Tamil answer: the heading is Tamil, because it follows the answer's script")
    void tamilAnswerInEnglishModeGetsTamilHeading() throws Exception {
        when(aiService.getAnswer(anyString(), anyString(), anyString())).thenReturn("இது தமிழ் பதில்");
        postVoice("Which subsidy schemes are there?", "english")
                .andExpect(jsonPath("$.tamilAnswer", containsString("பொருத்தமான அரசு திட்டங்கள்:")))
                .andExpect(jsonPath("$.tamilAnswer", not(containsString("Matching Government Schemes"))));
    }

    @Test
    @DisplayName("An English scheme question gets the list under an English heading")
    void englishSchemeQuestion() throws Exception {
        postVoice("Is there a crop insurance scheme for tomato farmers?", "english")
                .andExpect(jsonPath("$.tamilAnswer", containsString("Matching Government Schemes:")))
                .andExpect(jsonPath("$.tamilAnswer", containsString("• PMFBY")));
    }
}
