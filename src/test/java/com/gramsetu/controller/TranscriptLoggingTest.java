package com.gramsetu.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gramsetu.dto.DialectResult;
import com.gramsetu.repository.FarmerQueryRepository;
import com.gramsetu.service.AIAdvisorService;
import com.gramsetu.service.DialectDetectionService;
import com.gramsetu.service.SarvamSpeechService;
import com.gramsetu.service.SchemeRecommendationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The transcript and answer text must not appear in the logs at any level (INFO or DEBUG);
 * only lengths are logged.
 */
@WebMvcTest(FarmerController.class)
class TranscriptLoggingTest {

    private static final String SECRET_WORDS = "FARMER-SAID-THIS-PRIVATE-SENTENCE";

    @Autowired MockMvc mvc;
    @MockBean SarvamSpeechService sarvamService;
    @MockBean AIAdvisorService aiService;
    @MockBean DialectDetectionService dialectService;
    @MockBean FarmerQueryRepository farmerQueryRepo;
    @MockBean SchemeRecommendationService schemeService;

    private final Logger appLogger = (Logger) LoggerFactory.getLogger("com.gramsetu");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        originalLevel = appLogger.getLevel();
        appender.start();
        appLogger.addAppender(appender);
        when(sarvamService.toText(any(), anyString())).thenReturn(SECRET_WORDS);
        when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
        when(dialectService.buildAnswerPrompt(anyString(), any(), anyString())).thenReturn("prompt");
        when(aiService.getAnswer(anyString(), anyString(), anyString())).thenReturn("an answer");
        when(schemeService.findMatchingSchemes(anyString(), anyString())).thenReturn(List.of());
        when(sarvamService.toAudio(anyString(), anyString())).thenReturn("");
    }

    @AfterEach
    void detachAppender() {
        appLogger.detachAppender(appender);
        appLogger.setLevel(originalLevel);
    }

    private void postVoice() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[200]);
        mvc.perform(multipart("/api/v1/farmer/voice").file(audio)).andExpect(status().isOk());
    }

    private boolean anyLogContains(String text) {
        return appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains(text));
    }

    @Test
    void atInfoTheTranscriptIsNotLoggedOnlyItsLength() throws Exception {
        appLogger.setLevel(Level.INFO);
        postVoice();
        assertFalse(anyLogContains(SECRET_WORDS), "transcript leaked into INFO logs");
        assertTrue(anyLogContains("STT result: " + SECRET_WORDS.length() + " chars"));
    }

    @Test
    void evenAtDebugTheTranscriptIsNeverLogged() throws Exception {
        appLogger.setLevel(Level.DEBUG);
        postVoice();
        assertFalse(anyLogContains(SECRET_WORDS), "the transcript must not be logged at any level");
        assertTrue(anyLogContains("STT result: " + SECRET_WORDS.length() + " chars"), "its length is still logged");
    }
}
