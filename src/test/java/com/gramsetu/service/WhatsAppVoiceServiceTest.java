package com.gramsetu.service;

import com.gramsetu.config.TwilioConfig;
import com.gramsetu.dto.DialectResult;
import com.gramsetu.dto.VoiceMessageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WhatsAppVoiceService.processVoiceMessage().
 *
 * Tests the four-stage pipeline:
 *   (1) Download audio from Twilio URL          — fatal on failure (or when over the size limit)
 *   (2) Sarvam STT: toText(bytes, mime)         — fatal on blank / error transcript
 *   (3) Dialect detection                        — never fatal, falls back to STANDARD
 *   (4) AI answer: getVoiceAnswer(question, dialect) — never fatal, falls back to the voice demo answer
 * Voice notes are answered with text only, so no speech synthesis is requested (see NoSpeechTests).
 *
 * All external dependencies (RestTemplate, services, TwilioConfig) are mocked.
 * The @Value("${twilio.auth.token}") field is set via ReflectionTestUtils since
 * Mockito's @InjectMocks does not process Spring @Value annotations.
 */
@ExtendWith(MockitoExtension.class)
class WhatsAppVoiceServiceTest {

    @InjectMocks
    private WhatsAppVoiceService voiceService;

    @Mock private SarvamSpeechService    sarvamService;
    @Mock private DialectDetectionService dialectService;
    @Mock private AIAdvisorService       aiService;
    @Mock private TwilioConfig           twilioConfig;
    @Mock private RestTemplate           restTemplate;

    private static final String AUDIO_URL  = "https://api.twilio.com/test-audio.ogg";
    private static final byte[] FAKE_AUDIO = "fake-ogg-audio-data".getBytes();
    private static final String TRANSCRIPT = "நெல் செடிக்கு மஞ்சள் நிற இலைகள் வந்துள்ளன";
    private static final String AI_ANSWER  = "நைட்ரஜன் குறைபாடு — யூரியா 1 கைப்பிடி போடுங்க";

    @BeforeEach
    void setUpCommonStubs() {
        // @Value field cannot be injected by Mockito; set it directly
        ReflectionTestUtils.setField(voiceService, "twilioAuthToken", "test-auth-token-32chars1234567");
        // TwilioConfig needed by downloadAudio for Basic auth credentials
        when(twilioConfig.getAccountSid()).thenReturn("ACtest123");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Happy path — all stages succeed
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Successful full pipeline")
    class SuccessfulPipelineTests {

        @Test
        @DisplayName("All stages succeed → success=true with transcript, dialect and answer")
        void fullPipeline_returnsCompleteSuccessResult() {
            when(restTemplate.exchange(
                    eq(AUDIO_URL), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(FAKE_AUDIO));
            when(sarvamService.toText(eq(FAKE_AUDIO), eq("audio/ogg")))
                .thenReturn(TRANSCRIPT);
            when(dialectService.detect(eq(TRANSCRIPT), eq("tamil"), eq("")))
                .thenReturn(DialectResult.kongu(80.0));
            when(aiService.getVoiceAnswer(eq(TRANSCRIPT), eq("KONGU")))
                .thenReturn(AI_ANSWER);

            VoiceMessageResult result = voiceService.processVoiceMessage(AUDIO_URL);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getFailedStep()).isNull();
            assertThat(result.getTranscript()).isEqualTo(TRANSCRIPT);
            assertThat(result.getDialect()).isEqualTo("KONGU");
            assertThat(result.getDialectRegion()).isEqualTo("Hosur / Coimbatore");
            assertThat(result.getAnswerText()).isEqualTo(AI_ANSWER);
        }

        @Test
        @DisplayName("Step 2 — Sarvam STT called with ogg MIME type")
        void sarvamSttCalledWithOggMimeType() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(FAKE_AUDIO));
            when(sarvamService.toText(any(byte[].class), anyString())).thenReturn(TRANSCRIPT);
            when(dialectService.detect(anyString(), anyString(), anyString()))
                .thenReturn(DialectResult.standard());
            when(aiService.getVoiceAnswer(anyString(), anyString())).thenReturn(AI_ANSWER);

            voiceService.processVoiceMessage(AUDIO_URL);

            // Key assertion: STT was called with the correct audio bytes and ogg MIME type
            verify(sarvamService).toText(eq(FAKE_AUDIO), eq("audio/ogg"));
        }

        @Test
        @DisplayName("Dialect region from result matches DialectResult factory value")
        void dialectRegion_propagatedToResult() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(FAKE_AUDIO));
            when(sarvamService.toText(any(), anyString())).thenReturn(TRANSCRIPT);
            when(dialectService.detect(anyString(), anyString(), anyString()))
                .thenReturn(DialectResult.madurai(60.0));
            when(aiService.getVoiceAnswer(anyString(), eq("MADURAI"))).thenReturn(AI_ANSWER);

            VoiceMessageResult result = voiceService.processVoiceMessage(AUDIO_URL);

            assertThat(result.getDialect()).isEqualTo("MADURAI");
            assertThat(result.getDialectRegion()).isEqualTo("Madurai / Dindigul");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Stage 1 — Download audio
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stage 1: Audio download")
    class DownloadStageTests {

        @Test
        @DisplayName("RestTemplate throws → success=false, failedStep=DOWNLOAD, no further calls")
        void downloadThrows_returnsDownloadFailure() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenThrow(new RuntimeException("Connection refused: api.twilio.com"));

            VoiceMessageResult result = voiceService.processVoiceMessage(AUDIO_URL);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailedStep()).isEqualTo("DOWNLOAD");
            assertThat(result.getAnswerText()).isNotBlank(); // Tamil error message present
            verifyNoInteractions(sarvamService, dialectService, aiService);
        }

        @Test
        @DisplayName("Audio larger than MAX_AUDIO_BYTES → treated as a failed download, STT never called")
        void downloadOverTheSizeLimit_returnsDownloadFailure() {
            ReflectionTestUtils.setField(voiceService, "maxAudioBytes", 10L);   // FAKE_AUDIO is 19 bytes
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(FAKE_AUDIO));

            VoiceMessageResult result = voiceService.processVoiceMessage(AUDIO_URL);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailedStep()).isEqualTo("DOWNLOAD");
            verifyNoInteractions(sarvamService, dialectService, aiService);
        }

        @Test
        @DisplayName("Audio exactly at the limit is accepted")
        void downloadAtTheSizeLimit_isAccepted() {
            ReflectionTestUtils.setField(voiceService, "maxAudioBytes", (long) FAKE_AUDIO.length);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(FAKE_AUDIO));
            when(sarvamService.toText(any(), anyString())).thenReturn(TRANSCRIPT);
            when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
            when(aiService.getVoiceAnswer(anyString(), anyString())).thenReturn(AI_ANSWER);

            assertThat(voiceService.processVoiceMessage(AUDIO_URL).isSuccess()).isTrue();
        }

        @Test
        @DisplayName("RestTemplate returns empty bytes → success=false, failedStep=DOWNLOAD")
        void downloadReturnsEmptyBytes_returnsDownloadFailure() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(new byte[0]));

            VoiceMessageResult result = voiceService.processVoiceMessage(AUDIO_URL);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailedStep()).isEqualTo("DOWNLOAD");
            verifyNoInteractions(sarvamService, dialectService, aiService);
        }

        @Test
        @DisplayName("RestTemplate returns null body → success=false, failedStep=DOWNLOAD")
        void downloadReturnsNullBody_returnsDownloadFailure() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(null));

            VoiceMessageResult result = voiceService.processVoiceMessage(AUDIO_URL);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailedStep()).isEqualTo("DOWNLOAD");
            verifyNoInteractions(sarvamService, dialectService, aiService);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Stage 2 — Sarvam STT
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stage 2: Sarvam STT (toText)")
    class SttStageTests {

        @BeforeEach
        void downloadSucceeds() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(FAKE_AUDIO));
        }

        @Test
        @DisplayName("toText returns blank → success=false, failedStep=STT, no AI call")
        void sttReturnsBlank_returnsSttFailure() {
            when(sarvamService.toText(any(), anyString())).thenReturn("   ");

            VoiceMessageResult result = voiceService.processVoiceMessage(AUDIO_URL);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailedStep()).isEqualTo("STT");
            assertThat(result.getAnswerText()).isNotBlank();
            verifyNoInteractions(dialectService, aiService);
        }

        @Test
        @DisplayName("toText returns Sarvam error string 'குரல் புரியவில்லை' → failedStep=STT")
        void sttReturnsKuralErrorString_returnsSttFailure() {
            when(sarvamService.toText(any(), anyString()))
                .thenReturn("குரல் புரியவில்லை — please try again");

            VoiceMessageResult result = voiceService.processVoiceMessage(AUDIO_URL);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailedStep()).isEqualTo("STT");
            verifyNoInteractions(dialectService, aiService);
        }

        @Test
        @DisplayName("toText returns 'மீண்டும் முயற்சிக்கவும்' error string → failedStep=STT")
        void sttReturnsRetryErrorString_returnsSttFailure() {
            when(sarvamService.toText(any(), anyString()))
                .thenReturn("மீண்டும் முயற்சிக்கவும்");

            VoiceMessageResult result = voiceService.processVoiceMessage(AUDIO_URL);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailedStep()).isEqualTo("STT");
        }

        @Test
        @DisplayName("toText throws RuntimeException → success=false, failedStep=STT")
        void sttThrows_returnsSttFailure() {
            when(sarvamService.toText(any(), anyString()))
                .thenThrow(new RuntimeException("Sarvam STT API timeout"));

            VoiceMessageResult result = voiceService.processVoiceMessage(AUDIO_URL);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailedStep()).isEqualTo("STT");
            verifyNoInteractions(dialectService, aiService);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Stage 3 — Dialect detection (never fatal)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stage 3: Dialect detection (non-fatal)")
    class DialectStageTests {

        @BeforeEach
        void downloadAndSttSucceed() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(FAKE_AUDIO));
            when(sarvamService.toText(any(), anyString())).thenReturn(TRANSCRIPT);
            when(aiService.getVoiceAnswer(anyString(), anyString())).thenReturn(AI_ANSWER);
        }

        @Test
        @DisplayName("Dialect service throws → falls back to STANDARD, pipeline continues")
        void dialectServiceThrows_fallsBackToStandard() {
            when(dialectService.detect(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Dialect service unavailable"));

            VoiceMessageResult result = voiceService.processVoiceMessage(AUDIO_URL);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDialect()).isEqualTo("STANDARD");
            // AI was still called with STANDARD dialect
            verify(aiService).getVoiceAnswer(anyString(), eq("STANDARD"));
        }

        @Test
        @DisplayName("Dialect called with transcript, 'tamil' language and an empty location (keyword-only)")
        void dialectCalledWithCorrectArgs() {
            when(dialectService.detect(eq(TRANSCRIPT), eq("tamil"), eq("")))
                .thenReturn(DialectResult.standard());

            voiceService.processVoiceMessage(AUDIO_URL);

            verify(dialectService).detect(TRANSCRIPT, "tamil", "");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Stage 4 — AI answer (never fatal)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stage 4: AI answer (non-fatal)")
    class AiStageTests {

        @BeforeEach
        void downloadSttDialectSucceed() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(FAKE_AUDIO));
            when(sarvamService.toText(any(), anyString())).thenReturn(TRANSCRIPT);
            when(dialectService.detect(anyString(), anyString(), anyString()))
                .thenReturn(DialectResult.standard());
        }

        @Test
        @DisplayName("AIAdvisorService throws → voice demo answer used, pipeline continues as success")
        void aiServiceThrows_usesDemoAnswer() {
            when(aiService.getVoiceAnswer(anyString(), anyString()))
                .thenThrow(new RuntimeException("Gemini API error"));
            when(aiService.getVoiceDemoAnswer()).thenReturn("Demo Tamil farming answer");

            VoiceMessageResult result = voiceService.processVoiceMessage(AUDIO_URL);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAnswerText()).isEqualTo("Demo Tamil farming answer");
            verify(aiService).getVoiceDemoAnswer();
        }

        @Test
        @DisplayName("AI called with detected dialect code")
        void aiCalledWithDetectedDialect() {
            when(dialectService.detect(anyString(), anyString(), anyString()))
                .thenReturn(DialectResult.nellai(55.0));
            when(aiService.getVoiceAnswer(anyString(), eq("NELLAI")))
                .thenReturn(AI_ANSWER);

            voiceService.processVoiceMessage(AUDIO_URL);

            verify(aiService).getVoiceAnswer(eq(TRANSCRIPT), eq("NELLAI"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // No speech synthesis: voice notes are answered with text only
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("No speech synthesis for voice notes")
    class NoSpeechTests {

        @BeforeEach
        void allStagesSucceed() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(FAKE_AUDIO));
            when(sarvamService.toText(any(), anyString())).thenReturn(TRANSCRIPT);
            when(dialectService.detect(anyString(), anyString(), anyString()))
                .thenReturn(DialectResult.chennai(65.0));
            when(aiService.getVoiceAnswer(anyString(), eq("CHENNAI"))).thenReturn(AI_ANSWER);
        }

        @Test
        @DisplayName("Sarvam is used for speech-to-text only; text-to-speech is never requested")
        void textToSpeechIsNeverRequested() {
            VoiceMessageResult result = voiceService.processVoiceMessage(AUDIO_URL);

            assertThat(result.isSuccess()).isTrue();
            // the only Sarvam interaction is the transcription: no paid TTS call is wasted on the voice path
            verify(sarvamService, only()).toText(any(byte[].class), anyString());
            verify(sarvamService, never()).toAudio(anyString(), anyString());
        }

        @Test
        @DisplayName("The result carries the answer text, transcript and dialect for the text reply")
        void resultCarriesEverythingTheTextReplyNeeds() {
            VoiceMessageResult result = voiceService.processVoiceMessage(AUDIO_URL);

            assertThat(result.getAnswerText()).isEqualTo(AI_ANSWER);
            assertThat(result.getTranscript()).isEqualTo(TRANSCRIPT);
            assertThat(result.getDialect()).isEqualTo("CHENNAI");
        }
    }
}
