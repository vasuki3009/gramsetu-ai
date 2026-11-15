package com.gramsetu.controller;

import com.gramsetu.dto.DialectResult;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The answer language on the website: English if the button is English or the question is clearly English,
 * Tamil otherwise. English answers get no Tamil dialect, an English prompt and the English voice.
 */
@WebMvcTest(FarmerController.class)
class AnswerLanguageEndpointTest {

    private static final String ENGLISH_QUESTION = "How do I join the PM Kisan scheme?";
    private static final String TAMIL_QUESTION = "தக்காளி இலை சுருள்கிறது, என்ன பண்றது?";

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
        when(sarvamService.toAudio(anyString(), anyString())).thenReturn("TAMIL-AUDIO");
        when(sarvamService.toEnglishAudio(anyString())).thenReturn("ENGLISH-AUDIO");
        when(schemeService.findMatchingSchemes(anyString(), anyString())).thenReturn(List.of());
    }

    private ResultActions ask(String question, String toggle) throws Exception {
        String body = "{\"question\":\"" + question + "\",\"language\":\"" + toggle + "\"}";
        return mvc.perform(post("/api/v1/farmer/ask").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private ResultActions voice(String transcript, String toggle) throws Exception {
        when(sarvamService.toText(any(), anyString())).thenReturn(transcript);
        when(sarvamService.toTextAutoDetect(any(), anyString())).thenReturn(transcript);
        MockMultipartFile audio = new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[200]);
        return mvc.perform(multipart("/api/v1/farmer/voice").file(audio).param("language", toggle))
                .andExpect(status().isOk());
    }

    // ── text ──

    @Test
    @DisplayName("English button + English question: English prompt, STANDARD dialect, English voice, answerLanguage=english")
    void englishButtonEnglishQuestion() throws Exception {
        ask(ENGLISH_QUESTION, "english")
                .andExpect(jsonPath("$.tamilAnswer").value("THE-ANSWER"))
                .andExpect(jsonPath("$.audioBase64").value("ENGLISH-AUDIO"))
                .andExpect(jsonPath("$.answerLanguage").value("english"))
                .andExpect(jsonPath("$.dialectDetected").value("STANDARD"));

        verify(dialectService).detect(eq(ENGLISH_QUESTION), eq("english"), anyString());
        verify(dialectService).buildAnswerPrompt(eq(ENGLISH_QUESTION), any(), eq("english"));
        verify(aiService).getAnswer(anyString(), eq("english"), eq("STANDARD"));
        verify(sarvamService).toEnglishAudio("THE-ANSWER");
        verify(sarvamService, never()).toAudio(anyString(), anyString());
    }

    @Test
    @DisplayName("Tamil button + clearly English question: the answer is English (the bug)")
    void tamilButtonEnglishQuestion() throws Exception {
        ask(ENGLISH_QUESTION, "tamil")
                .andExpect(jsonPath("$.answerLanguage").value("english"))
                .andExpect(jsonPath("$.audioBase64").value("ENGLISH-AUDIO"));

        verify(aiService).getAnswer(anyString(), eq("english"), anyString());
        verify(sarvamService, never()).toAudio(anyString(), anyString());
    }

    @Test
    @DisplayName("Tamil button + Tamil question: Tamil answer with the dialect voice")
    void tamilButtonTamilQuestion() throws Exception {
        ask(TAMIL_QUESTION, "tamil")
                .andExpect(jsonPath("$.answerLanguage").value("tamil"))
                .andExpect(jsonPath("$.audioBase64").value("TAMIL-AUDIO"));

        verify(aiService).getAnswer(anyString(), eq("tamil"), anyString());
        verify(sarvamService, never()).toEnglishAudio(anyString());
    }

    @Test
    @DisplayName("Tamil typed in English letters stays Tamil")
    void tanglishStaysTamil() throws Exception {
        ask("en thakkali ilai surundu pogudhu enna pannalam", "tamil")
                .andExpect(jsonPath("$.answerLanguage").value("tamil"));
        verify(aiService).getAnswer(anyString(), eq("tamil"), anyString());
    }

    @Test
    @DisplayName("If the English voice fails the website gets no audio and falls back to the browser voice")
    void englishAudioFailureGivesEmptyAudio() throws Exception {
        when(sarvamService.toEnglishAudio(anyString())).thenReturn("");
        ask(ENGLISH_QUESTION, "english")
                .andExpect(jsonPath("$.tamilAnswer").value("THE-ANSWER"))
                .andExpect(jsonPath("$.audioBase64").value(""))
                .andExpect(jsonPath("$.answerLanguage").value("english"));
    }

    // ── voice ──

    @Test
    @DisplayName("English mode listens with auto-detection; Tamil mode keeps Tamil speech-to-text")
    void speechToTextMode() throws Exception {
        voice(ENGLISH_QUESTION, "english")
                .andExpect(jsonPath("$.answerLanguage").value("english"))
                .andExpect(jsonPath("$.transcript").value(ENGLISH_QUESTION));
        verify(sarvamService).toTextAutoDetect(any(), anyString());
        verify(sarvamService, never()).toText(any(), anyString());

        voice(TAMIL_QUESTION, "tamil")
                .andExpect(jsonPath("$.answerLanguage").value("tamil"));
        verify(sarvamService).toText(any(), anyString());
    }

    @Test
    @DisplayName("A spoken English question with the Tamil button is answered in English")
    void spokenEnglishWithTamilButton() throws Exception {
        voice(ENGLISH_QUESTION, "tamil")
                .andExpect(jsonPath("$.answerLanguage").value("english"))
                .andExpect(jsonPath("$.audioBase64").value("ENGLISH-AUDIO"));
        verify(aiService).getAnswer(anyString(), eq("english"), anyString());
    }

    @Test
    @DisplayName("Rejections and errors report the language of the button")
    void errorsCarryTheLanguage() throws Exception {
        mvc.perform(post("/api/v1/farmer/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\",\"language\":\"english\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerLanguage").value("english"));
        mvc.perform(post("/api/v1/farmer/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\",\"language\":\"tamil\"}"))
                .andExpect(jsonPath("$.answerLanguage").value("tamil"));
    }
}
