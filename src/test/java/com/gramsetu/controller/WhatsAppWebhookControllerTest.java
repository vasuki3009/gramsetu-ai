package com.gramsetu.controller;

import com.gramsetu.config.AsyncConfig;
import com.gramsetu.config.TwilioConfig;
import com.gramsetu.dto.CropResponse;
import com.gramsetu.dto.DialectResult;
import com.gramsetu.dto.VoiceMessageResult;
import com.gramsetu.repository.CropAdvisoryRepository;
import com.gramsetu.repository.FarmerQueryRepository;
import com.gramsetu.repository.GovernmentSchemeRepository;
import com.gramsetu.service.AIAdvisorService;
import com.gramsetu.service.ChatTurn;
import com.gramsetu.service.CloudinaryService;
import com.gramsetu.service.CropVisionService;
import com.gramsetu.service.DialectDetectionService;
import com.gramsetu.service.DialectTranslationService;
import com.gramsetu.service.SarvamSpeechService;
import com.gramsetu.service.WhatsAppMessageSender;
import com.gramsetu.service.WhatsAppVoiceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executor;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for the Twilio WhatsApp webhook.
 *
 * Only the controller is loaded (@WebMvcTest); collaborators are Mockito mocks. Replies to the
 * farmer go through {@link WhatsAppMessageSender}, which is mocked, so the tests assert exactly
 * what would be sent. The webhook itself always answers 200 with an empty body: the real reply is
 * sent through the Twilio API. The Twilio signature filter is covered separately
 * (TwilioSignatureFilterTest) and is not part of this slice.
 *
 * The background audio job runs on a direct (same-thread) executor here so tests are deterministic.
 * A text message asks the AI exactly once and detects the dialect exactly once: the spoken follow-up is
 * built from that same answer (see textMessage_audioReusesTheAnswer_noSecondAiCall).
 */
@WebMvcTest(WhatsAppWebhookController.class)
@Import(WhatsAppWebhookControllerTest.DirectExecutorConfig.class)
@TestPropertySource(properties = {
    "twilio.account.sid=ACtest1234567890abcdef1234567890ab",
    "twilio.auth.token=testtoken12345678901234567890abcd",
    "twilio.whatsapp.from=whatsapp:+10000000000",
    "app.limits.max-question-length=60",
    "app.limits.max-image-bytes=1000"
})
class WhatsAppWebhookControllerTest {

    /** Runs the background audio job inline. */
    @TestConfiguration
    static class DirectExecutorConfig {
        @Bean(name = AsyncConfig.AUDIO_REPLY_EXECUTOR)
        Executor audioReplyExecutor() {
            return Runnable::run;
        }

        /** Inline by default; a test can switch it to "hold" so the work only runs when the test says so. */
        @Bean(name = AsyncConfig.MESSAGE_REPLY_EXECUTOR)
        HoldableExecutor messageReplyExecutor() {
            return new HoldableExecutor();
        }
    }

    static class HoldableExecutor implements Executor {
        volatile boolean hold;
        final java.util.List<Runnable> queued = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override public void execute(Runnable r) {
            if (hold) queued.add(r); else r.run();
        }
        void reset() { hold = false; queued.clear(); }
    }

    @Autowired
    private HoldableExecutor messageExecutor;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WhatsAppWebhookController webhookController;

    // ── Mocked dependencies ────────────────────────────────────────────────────
    @MockBean private AIAdvisorService            aiService;
    @MockBean private SarvamSpeechService         sarvamService;
    @MockBean private DialectDetectionService     dialectService;
    @MockBean private DialectTranslationService   dialectTranslationService;
    @MockBean private CropVisionService           cropVisionService;
    @MockBean private CloudinaryService           cloudinaryService;
    @MockBean private TwilioConfig                twilioConfig;
    @MockBean private WhatsAppVoiceService        voiceService;
    @MockBean private WhatsAppMessageSender       messageSender;
    @MockBean private FarmerQueryRepository       farmerQueryRepo;
    @MockBean private CropAdvisoryRepository      cropAdvisoryRepo;
    @MockBean private GovernmentSchemeRepository  schemeRepo;
    @MockBean private RestTemplate                restTemplate;

    private static final String FARMER_NUMBER = "whatsapp:+10000000001";
    private static final String AUDIO_URL     = "https://api.twilio.com/test-audio.ogg";
    private static final String IMAGE_URL     = "https://api.twilio.com/test-image.jpg";
    private static final byte[] FAKE_IMAGE    = "fake-jpeg-bytes".getBytes();
    private static final String CDN_URL       = "https://cdn.example.test/reply.mp3";

    private static final String APOLOGY = "மன்னிக்கவும், சேவை தற்காலிகமாக";   // generic "service unavailable" reply

    @BeforeEach
    void setUp() {
        // Explicit isolation: the shared @MockBean mocks are not reliably reset between tests of the
        // @Nested groups, which let stubs and recorded calls from one test leak into the next.
        messageExecutor.reset();
        Mockito.reset(aiService, sarvamService, dialectService, dialectTranslationService, cropVisionService,
                cloudinaryService, twilioConfig, voiceService, messageSender, farmerQueryRepo,
                cropAdvisoryRepo, schemeRepo, restTemplate);

        when(twilioConfig.getAccountSid()).thenReturn("ACtest1234567890abcdef1234567890ab");
        when(twilioConfig.getWhatsappFrom()).thenReturn("whatsapp:+10000000000");
        // the dialect rewrite is a pass-through in these tests
        when(dialectTranslationService.transformToDialect(anyString(), anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private ResultActions postText(String from, String body) throws Exception {
        return mockMvc.perform(post("/api/whatsapp/webhook")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("From", from)
                .param("Body", body)
                .param("NumMedia", "0"));
    }

    private ResultActions postMedia(String from, String mediaUrl, String contentType) throws Exception {
        return mockMvc.perform(post("/api/whatsapp/webhook")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("From", from)
                .param("Body", "")
                .param("NumMedia", "1")
                .param("MediaUrl0", mediaUrl)
                .param("MediaContentType0", contentType));
    }

    private void assertAccepted(ResultActions result) throws Exception {
        result.andExpect(status().isOk()).andExpect(content().string(""));
    }

    private void verifyTextSent(String to, String fragment) {
        verify(messageSender).send(eq(to), contains(fragment), isNull());
    }

    private void verifyNoMediaSent() {
        verify(messageSender, never()).send(any(), any(), isNotNull());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. Text messages
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Twilio's ~15 s webhook timeout: 200 first, AI work in the background")
    class AsyncReplyTests {

        @Test
        @DisplayName("The HTTP call returns 200 before the AI has answered; the reply is sent afterwards")
        void returns200BeforeAiAnswers() throws Exception {
            messageExecutor.hold = true;
            when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
            when(aiService.getAnswer(anyString(), anyString(), anyString(), anyList())).thenReturn("the answer");

            postText(FARMER_NUMBER, "நெல் பூச்சி").andExpect(status().isOk());

            verify(aiService, never()).getAnswer(anyString(), anyString(), anyString(), anyList());   // not called on the HTTP thread
            verify(messageSender, never()).send(any(), any(), any());
            org.junit.jupiter.api.Assertions.assertEquals(1, messageExecutor.queued.size());

            messageExecutor.queued.get(0).run();   // the background worker runs now

            verify(aiService).getAnswer(anyString(), anyString(), anyString(), anyList());
            verify(messageSender).send(eq(FARMER_NUMBER), contains("the answer"), isNull());
        }
    }

    @Nested
    @DisplayName("Text messages")
    class TextMessageTests {

        @Test
        @DisplayName("Tamil text → dialect detected, AI answers, reply carries the dialect label and answer")
        void textMessage_callsDialectThenAi() throws Exception {
            String question = "நெல்லுக்கு உரம் என்ன போட வேண்டும்?";
            when(dialectService.detect(anyString(), eq("tamil"), eq(""))).thenReturn(DialectResult.kongu(75.0));
            when(aiService.getAnswer(anyString(), eq("tamil"), eq("KONGU"), anyList())).thenReturn("UREA-50-KG-ANSWER");

            assertAccepted(postText(FARMER_NUMBER, question));

            verify(dialectService).detect(question, "tamil", "");
            verify(aiService).getAnswer(eq(question), eq("tamil"), eq("KONGU"), anyList());
            verify(messageSender).send(eq(FARMER_NUMBER),
                argThat(t -> t.contains("UREA-50-KG-ANSWER") && t.contains("கொங்கு தமிழ்")), isNull());
        }

        @Test
        @DisplayName("Standard dialect → STANDARD passed to the AI and no dialect label in the reply")
        void textMessage_standardDialect_passedToAi() throws Exception {
            when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
            when(aiService.getAnswer(anyString(), eq("tamil"), eq("STANDARD"), anyList())).thenReturn("PLAIN-ANSWER");

            assertAccepted(postText(FARMER_NUMBER, "தண்ணீர் எவ்வளவு போட வேண்டும்?"));

            verify(aiService).getAnswer(anyString(), eq("tamil"), eq("STANDARD"), anyList());
            verify(messageSender).send(eq(FARMER_NUMBER),
                argThat(t -> t.contains("PLAIN-ANSWER") && !t.contains("தமிழ்* ")), isNull());
        }

        @Test
        @DisplayName("Empty body → welcome message, AI, dialect, speech and upload NOT used")
        void emptyBody_returnsWelcome_noServiceCalls() throws Exception {
            assertAccepted(postText(FARMER_NUMBER, ""));

            verifyTextSent(FARMER_NUMBER, "GramSetu");
            verifyNoInteractions(aiService, dialectService, sarvamService, cloudinaryService);
            verifyNoMediaSent();
        }

        @Test
        @DisplayName("Whitespace-only body is treated like an empty one")
        void whitespaceBody_returnsWelcome_noServiceCalls() throws Exception {
            assertAccepted(postText(FARMER_NUMBER, "   "));

            verifyTextSent(FARMER_NUMBER, "GramSetu");
            verifyNoInteractions(aiService, dialectService, sarvamService, cloudinaryService);
        }

        @Test
        @DisplayName("Text longer than MAX_QUESTION_LENGTH → polite 'too long' reply, AI NOT called")
        void textOverTheLimit_isRejectedPolitely() throws Exception {
            assertAccepted(postText(FARMER_NUMBER, "x".repeat(61)));

            verifyTextSent(FARMER_NUMBER, "60");                  // the limit is named in the reply
            verifyNoInteractions(aiService, dialectService, sarvamService);
        }

        @Test
        @DisplayName("Text exactly at the limit is answered")
        void textAtTheLimit_isAnswered() throws Exception {
            when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
            when(aiService.getAnswer(anyString(), anyString(), anyString(), anyList())).thenReturn("OK-ANSWER");

            assertAccepted(postText(FARMER_NUMBER, "x".repeat(60)));

            verifyTextSent(FARMER_NUMBER, "OK-ANSWER");
        }

        @Test
        @DisplayName("'english' / 'tamil' switch the language for that number's following questions")
        void languageSwitch_appliesToLaterQuestions() throws Exception {
            String user = "whatsapp:+10000000077";   // own number: language preferences are kept per number
            when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
            when(aiService.getAnswer(anyString(), anyString(), anyString(), anyList())).thenReturn("ANSWER");

            assertAccepted(postText(user, "english"));
            verifyTextSent(user, "English");

            assertAccepted(postText(user, "How much urea for paddy?"));
            verify(aiService).getAnswer(eq("How much urea for paddy?"), eq("english"), eq("STANDARD"), anyList());

            assertAccepted(postText(user, "tamil"));
            assertAccepted(postText(user, "நெல் உரம்?"));
            verify(aiService).getAnswer(eq("நெல் உரம்?"), eq("tamil"), eq("STANDARD"), anyList());
        }

        @Test
        @DisplayName("One AI call and one dialect detection per text; the audio is built from that same answer")
        void textMessage_audioReusesTheAnswer_noSecondAiCall() throws Exception {
            when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.kongu(70.0));
            when(aiService.getAnswer(anyString(), anyString(), anyString(), anyList())).thenReturn("THE-ONE-ANSWER");
            when(dialectTranslationService.transformToDialect(anyString(), anyString()))
                .thenAnswer(invocation -> "KONGU-STYLE:" + invocation.getArgument(0));
            when(sarvamService.toAudioLong(anyString(), anyString()))
                .thenReturn(Base64.getEncoder().encodeToString("audio-bytes".getBytes()));
            when(cloudinaryService.uploadAudio(any(), anyString())).thenReturn(CDN_URL);

            assertAccepted(postText(FARMER_NUMBER, "நெல் உரம் என்ன?"));

            // exactly one LLM call and one detection, even though a spoken reply was produced
            verify(aiService, times(1)).getAnswer(anyString(), anyString(), anyString(), anyList());
            verify(dialectService, times(1)).detect(anyString(), anyString(), anyString());
            // the speech is made from that answer, rewritten into the detected dialect
            verify(dialectTranslationService).transformToDialect("THE-ONE-ANSWER", "KONGU");
            verify(sarvamService).toAudioLong("KONGU-STYLE:THE-ONE-ANSWER", "KONGU");
            verify(messageSender).send(eq(FARMER_NUMBER), eq(""), eq(CDN_URL));
        }

        @Test
        @DisplayName("Audio follow-up: speech generated, uploaded and sent as a media message after the text")
        void textMessage_audioFollowUpIsSentWhenUploadSucceeds() throws Exception {
            when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
            when(aiService.getAnswer(anyString(), anyString(), anyString(), anyList())).thenReturn("ANSWER");
            when(sarvamService.toAudioLong(anyString(), anyString()))
                .thenReturn(Base64.getEncoder().encodeToString("audio-bytes".getBytes()));
            when(cloudinaryService.uploadAudio(any(), anyString())).thenReturn(CDN_URL);

            assertAccepted(postText(FARMER_NUMBER, "நெல் உரம் என்ன?"));

            verify(messageSender).send(eq(FARMER_NUMBER), eq(""), eq(CDN_URL));
        }

        @Test
        @DisplayName("Audio upload fails (null URL) → no empty media message, text reply still delivered")
        void textMessage_uploadFailure_sendsNoMediaMessage() throws Exception {
            when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
            when(aiService.getAnswer(anyString(), anyString(), anyString(), anyList())).thenReturn("ANSWER-TEXT");
            when(sarvamService.toAudioLong(anyString(), anyString()))
                .thenReturn(Base64.getEncoder().encodeToString("audio-bytes".getBytes()));
            when(cloudinaryService.uploadAudio(any(), anyString())).thenReturn(null);

            assertAccepted(postText(FARMER_NUMBER, "நெல் உரம் என்ன?"));

            verifyTextSent(FARMER_NUMBER, "ANSWER-TEXT");
            verifyNoMediaSent();
        }

        @Test
        @DisplayName("Speech synthesis unavailable (no audio) → text only")
        void textMessage_noAudioFromTts_textOnly() throws Exception {
            when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
            when(aiService.getAnswer(anyString(), anyString(), anyString(), anyList())).thenReturn("ANSWER-TEXT");
            when(sarvamService.toAudioLong(anyString(), anyString())).thenReturn("");

            assertAccepted(postText(FARMER_NUMBER, "நெல் உரம் என்ன?"));

            verifyTextSent(FARMER_NUMBER, "ANSWER-TEXT");
            verifyNoMediaSent();
            verifyNoInteractions(cloudinaryService);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. Voice notes (the pipeline itself is covered by WhatsAppVoiceServiceTest)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Voice/audio messages")
    class AudioMessageTests {

        private VoiceMessageResult success(String dialect) {
            return VoiceMessageResult.builder()
                .success(true)
                .transcript("TRANSCRIPT-TEXT")
                .dialect(dialect)
                .dialectRegion("Region")
                .answerText("VOICE-ANSWER")
                .build();
        }

        @Test
        @DisplayName("audio/ogg → voice pipeline called with the media URL; text reply has transcript, label and answer")
        void audioMessage_delegatesToVoicePipelineAndRepliesWithText() throws Exception {
            when(voiceService.processVoiceMessage(AUDIO_URL)).thenReturn(success("KONGU"));

            assertAccepted(postMedia(FARMER_NUMBER, AUDIO_URL, "audio/ogg"));

            verify(voiceService).processVoiceMessage(AUDIO_URL);
            verify(messageSender).send(eq(FARMER_NUMBER),
                argThat(t -> t.contains("TRANSCRIPT-TEXT") && t.contains("VOICE-ANSWER") && t.contains("கொங்கு தமிழ்")),
                isNull());
        }

        @Test
        @DisplayName("Voice notes get a text reply only: no audio/media message is sent")
        void audioMessage_noMediaReply() throws Exception {
            when(voiceService.processVoiceMessage(AUDIO_URL)).thenReturn(success("MADURAI"));

            assertAccepted(postMedia(FARMER_NUMBER, AUDIO_URL, "audio/ogg"));

            verifyNoMediaSent();
            verifyNoInteractions(cloudinaryService);
        }

        @Test
        @DisplayName("audio/webm is routed to the voice pipeline too")
        void audioMessage_webm_isRoutedToTheVoicePipeline() throws Exception {
            String webmUrl = "https://api.twilio.com/test-audio.webm";
            when(voiceService.processVoiceMessage(webmUrl)).thenReturn(success("STANDARD"));

            assertAccepted(postMedia(FARMER_NUMBER, webmUrl, "audio/webm"));

            verify(voiceService).processVoiceMessage(webmUrl);
        }

        @Test
        @DisplayName("Download failure (DOWNLOAD step) → friendly 'could not download' message")
        void audioMessage_downloadFailure_repliesWithDownloadMessage() throws Exception {
            when(voiceService.processVoiceMessage(AUDIO_URL)).thenReturn(
                VoiceMessageResult.builder().success(false).failedStep("DOWNLOAD").build());

            assertAccepted(postMedia(FARMER_NUMBER, AUDIO_URL, "audio/ogg"));

            verifyTextSent(FARMER_NUMBER, "பதிவிறக்க");
            verifyNoMediaSent();
        }

        @Test
        @DisplayName("Speech not understood (STT step) → friendly 'speak clearly' message")
        void audioMessage_sttFailure_repliesWithSttMessage() throws Exception {
            when(voiceService.processVoiceMessage(AUDIO_URL)).thenReturn(
                VoiceMessageResult.builder().success(false).failedStep("STT").build());

            assertAccepted(postMedia(FARMER_NUMBER, AUDIO_URL, "audio/ogg"));

            verifyTextSent(FARMER_NUMBER, "குரல் புரியவில்லை");
        }

        @Test
        @DisplayName("Voice pipeline throws → 200 and a generic apology")
        void audioMessage_pipelineThrows_repliesWithApology() throws Exception {
            when(voiceService.processVoiceMessage(AUDIO_URL)).thenThrow(new RuntimeException("Sarvam API timeout"));

            assertAccepted(postMedia(FARMER_NUMBER, AUDIO_URL, "audio/ogg"));

            verifyTextSent(FARMER_NUMBER, APOLOGY);
        }

        @Test
        @DisplayName("The controller itself does no STT, dialect or AI work for voice notes")
        void audioMessage_controllerDoesNotRunThePipelineItself() throws Exception {
            when(voiceService.processVoiceMessage(AUDIO_URL)).thenReturn(success("STANDARD"));

            assertAccepted(postMedia(FARMER_NUMBER, AUDIO_URL, "audio/ogg"));

            verifyNoInteractions(sarvamService, dialectService, aiService, restTemplate);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. Image messages
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Image messages")
    class ImageMessageTests {

        private CropResponse diagnosis(String text) {
            return new CropResponse(text, false, null, "");
        }

        @Test
        @DisplayName("image/jpeg → downloaded, analysed by CropVisionService, diagnosis sent")
        void imageMessage_callsCropVisionWithImageBytes() throws Exception {
            when(restTemplate.exchange(eq(IMAGE_URL), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(FAKE_IMAGE));
            when(cropVisionService.analyzeCropImage(eq(FAKE_IMAGE), eq("tamil")))
                .thenReturn(diagnosis("நோய்: BLAST-DISEASE\nசிகிச்சை: Spray fungicide\nதடுப்பு முறைகள்: Crop rotation"));

            assertAccepted(postMedia(FARMER_NUMBER, IMAGE_URL, "image/jpeg"));

            verify(cropVisionService).analyzeCropImage(eq(FAKE_IMAGE), eq("tamil"));
            verify(messageSender).send(eq(FARMER_NUMBER), contains("BLAST-DISEASE"), isNull());
        }

        @Test
        @DisplayName("image/png also routes to CropVisionService")
        void imageMessage_png_callsCropVisionService() throws Exception {
            String pngUrl = "https://api.twilio.com/test-image.png";
            when(restTemplate.exchange(eq(pngUrl), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(FAKE_IMAGE));
            when(cropVisionService.analyzeCropImage(any(), anyString())).thenReturn(diagnosis("நோய்: rust"));

            assertAccepted(postMedia(FARMER_NUMBER, pngUrl, "image/png"));

            verify(cropVisionService).analyzeCropImage(any(byte[].class), eq("tamil"));
        }

        @Test
        @DisplayName("Diagnosis audio is uploaded and sent as a media message after the text")
        void imageMessage_diagnosisAudioIsSent() throws Exception {
            when(restTemplate.exchange(eq(IMAGE_URL), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(FAKE_IMAGE));
            when(cropVisionService.analyzeCropImage(any(), anyString()))
                .thenReturn(diagnosis("நோய்: rust\nசிகிச்சை: spray"));
            when(sarvamService.toAudioLong(anyString(), anyString()))
                .thenReturn(Base64.getEncoder().encodeToString("audio-bytes".getBytes()));
            when(cloudinaryService.uploadAudio(any(), anyString())).thenReturn(CDN_URL);

            assertAccepted(postMedia(FARMER_NUMBER, IMAGE_URL, "image/jpeg"));

            verify(messageSender).send(eq(FARMER_NUMBER), eq(""), eq(CDN_URL));
        }

        @Test
        @DisplayName("Diagnosis audio upload fails → diagnosis text only, no media message")
        void imageMessage_uploadFailure_textOnly() throws Exception {
            when(restTemplate.exchange(eq(IMAGE_URL), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(FAKE_IMAGE));
            when(cropVisionService.analyzeCropImage(any(), anyString())).thenReturn(diagnosis("நோய்: DIAG-TEXT"));
            when(sarvamService.toAudioLong(anyString(), anyString()))
                .thenReturn(Base64.getEncoder().encodeToString("audio-bytes".getBytes()));
            when(cloudinaryService.uploadAudio(any(), anyString())).thenReturn(null);

            assertAccepted(postMedia(FARMER_NUMBER, IMAGE_URL, "image/jpeg"));

            verifyTextSent(FARMER_NUMBER, "DIAG-TEXT");
            verifyNoMediaSent();
        }

        @Test
        @DisplayName("Image larger than MAX_IMAGE_BYTES → treated as a failed download, CropVision NOT called")
        void imageMessage_tooLarge_cropVisionNotCalled() throws Exception {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(new byte[2000]));   // limit is 1000 in this test

            assertAccepted(postMedia(FARMER_NUMBER, IMAGE_URL, "image/jpeg"));

            verifyTextSent(FARMER_NUMBER, "பதிவிறக்க");
            verifyNoInteractions(cropVisionService);
        }

        @Test
        @DisplayName("Image download fails (null body) → CropVision NOT called, friendly message")
        void imageMessage_downloadReturnsNull_cropVisionNotCalled() throws Exception {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(null));

            assertAccepted(postMedia(FARMER_NUMBER, IMAGE_URL, "image/jpeg"));

            verifyTextSent(FARMER_NUMBER, "பதிவிறக்க");
            verifyNoInteractions(cropVisionService);
        }

        @Test
        @DisplayName("Image download throws → CropVision NOT called, graceful 200")
        void imageMessage_downloadThrows_cropVisionNotCalled() throws Exception {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenThrow(new RuntimeException("503 Service Unavailable"));

            assertAccepted(postMedia(FARMER_NUMBER, IMAGE_URL, "image/jpeg"));

            verifyNoInteractions(cropVisionService);
        }

        @Test
        @DisplayName("Unsupported media type (PDF) → 'not supported' reply, no service calls")
        void unsupportedMediaType_noServiceCalls() throws Exception {
            assertAccepted(postMedia(FARMER_NUMBER, "https://api.twilio.com/doc.pdf", "application/pdf"));

            verifyTextSent(FARMER_NUMBER, "ஆதரிக்கப்படவில்லை");
            verifyNoInteractions(sarvamService, cropVisionService, aiService, dialectService, voiceService);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. Several messages
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Session persistence")
    class SessionPersistenceTests {

        @BeforeEach
        void setUpStubs() {
            when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
            when(aiService.getAnswer(anyString(), anyString(), anyString(), anyList())).thenReturn("AI பதில்");
        }

        @Test
        @DisplayName("Two messages from the same number each get their own AI call and reply")
        void sameNumber_twoMessages_eachGetIndependentAiCall() throws Exception {
            assertAccepted(postText(FARMER_NUMBER, "நெல்லுக்கு உரம் என்ன?"));
            assertAccepted(postText(FARMER_NUMBER, "தண்ணீர் எவ்வளவு?"));

            verify(aiService).getAnswer(eq("நெல்லுக்கு உரம் என்ன?"), eq("tamil"), anyString(), anyList());
            verify(aiService).getAnswer(eq("தண்ணீர் எவ்வளவு?"), eq("tamil"), anyString(), anyList());
            verify(dialectService).detect(eq("நெல்லுக்கு உரம் என்ன?"), eq("tamil"), eq(""));
            verify(dialectService).detect(eq("தண்ணீர் எவ்வளவு?"), eq("tamil"), eq(""));
        }

        @Test
        @DisplayName("Messages from different numbers are answered to their own sender")
        void differentNumbers_processedIndependently() throws Exception {
            String number1 = "whatsapp:+10000000011";
            String number2 = "whatsapp:+10000000012";

            assertAccepted(postText(number1, "நெல்லுக்கு நோய்"));
            assertAccepted(postText(number2, "மிளகாய் பயிர் பராமரிப்பு"));

            verify(messageSender).send(eq(number1), contains("AI"), isNull());
            verify(messageSender).send(eq(number2), contains("AI"), isNull());
        }

        @Test
        @DisplayName("Text then voice note from the same number each route correctly")
        void sameNumber_textThenAudio_eachRoutesCorrectly() throws Exception {
            when(voiceService.processVoiceMessage(AUDIO_URL)).thenReturn(
                VoiceMessageResult.builder().success(true).transcript("T").dialect("STANDARD")
                    .dialectRegion("Tamil Nadu").answerText("VOICE-ANSWER").build());

            assertAccepted(postText(FARMER_NUMBER, "நெல் பயிர் கேள்வி"));
            assertAccepted(postMedia(FARMER_NUMBER, AUDIO_URL, "audio/ogg"));

            verify(aiService).getAnswer(eq("நெல் பயிர் கேள்வி"), eq("tamil"), anyString(), anyList());
            verify(voiceService).processVoiceMessage(AUDIO_URL);
            // the voice pipeline (mocked) owns speech-to-text; the controller never transcribes
            verify(sarvamService, never()).toText(any(), anyString());
        }
    }

    @Nested
    @DisplayName("Answer language: the question's script decides, the english / tamil command only breaks ties")
    class AnswerLanguageTests {

        @BeforeEach
        void stubs() {
            when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
            when(aiService.getAnswer(anyString(), anyString(), anyString(), anyList())).thenReturn("THE-ANSWER");
        }

        @Test
        @DisplayName("An English question gets an English answer without saying 'english' first: no dialect header, English voice")
        void englishQuestionEnglishAnswer() throws Exception {
            String user = "whatsapp:+10000000991";   // no saved command: Tamil by default
            when(sarvamService.toEnglishAudio(anyString()))
                .thenReturn(Base64.getEncoder().encodeToString("audio-bytes".getBytes()));
            when(cloudinaryService.uploadAudio(any(), anyString())).thenReturn(CDN_URL);

            assertAccepted(postText(user, "How do I join the PM Kisan scheme?"));

            verify(aiService).getAnswer(eq("How do I join the PM Kisan scheme?"), eq("english"), eq("STANDARD"), anyList());
            verify(messageSender).send(eq(user), argThat(t -> t.equals("THE-ANSWER")), isNull());   // no dialect header
            verify(sarvamService).toEnglishAudio("THE-ANSWER");
            verify(sarvamService, never()).toAudioLong(anyString(), anyString());
            verifyNoInteractions(dialectTranslationService);                                         // no Tamil dialect rewriting
            verify(messageSender).send(eq(user), eq(""), eq(CDN_URL));
        }

        @Test
        @DisplayName("If the English voice fails the farmer simply keeps the text reply")
        void englishAudioFailureKeepsText() throws Exception {
            String user = "whatsapp:+10000000992";
            when(sarvamService.toEnglishAudio(anyString())).thenReturn("");

            assertAccepted(postText(user, "Which fertiliser should I use this month?"));

            verify(messageSender).send(eq(user), eq("THE-ANSWER"), isNull());
            verifyNoMediaSent();
            verifyNoInteractions(cloudinaryService);
        }

        @Test
        @DisplayName("Tamil script is Tamil even if the saved command is english")
        void tamilScriptBeatsTheCommand() throws Exception {
            String user = "whatsapp:+10000000993";
            assertAccepted(postText(user, "english"));
            assertAccepted(postText(user, "நெல்லுக்கு உரம் என்ன?"));

            verify(aiService).getAnswer(eq("நெல்லுக்கு உரம் என்ன?"), eq("tamil"), anyString(), anyList());
        }

        @Test
        @DisplayName("Unclear text ('ok', numbers) follows the saved command; with none it is Tamil")
        void unclearTextFollowsTheCommand() throws Exception {
            String chose = "whatsapp:+10000000994";
            String none = "whatsapp:+10000000995";
            assertAccepted(postText(chose, "english"));
            assertAccepted(postText(chose, "ok"));
            assertAccepted(postText(chose, "2"));
            assertAccepted(postText(none, "ok"));

            verify(aiService).getAnswer(eq("ok"), eq("english"), anyString(), anyList());
            verify(aiService).getAnswer(eq("2"), eq("english"), anyString(), anyList());
            verify(aiService).getAnswer(eq("ok"), eq("tamil"), anyString(), anyList());
        }
    }

    @Nested
    @DisplayName("Dialect override")
    class DialectOverrideTests {

        @BeforeEach
        void stubs() {
            when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
            when(aiService.getAnswer(anyString(), anyString(), anyString(), anyList())).thenReturn("ANSWER");
        }

        @Test
        @DisplayName("\"dialect chennai\" is confirmed in Tamil, then used for later messages instead of detection")
        void overrideAppliesToLaterMessages() throws Exception {
            String number = "whatsapp:+10000000881";

            assertAccepted(postText(number, "dialect chennai"));
            verify(messageSender).send(eq(number), contains("சென்னை தமிழில்"), isNull());
            verifyNoInteractions(aiService, dialectService);

            assertAccepted(postText(number, "தக்காளி இலை சுருள்கிறது"));
            verify(aiService).getAnswer(eq("தக்காளி இலை சுருள்கிறது"), eq("tamil"), eq("CHENNAI"), anyList());
            verify(dialectService, never()).detect(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Tamil command \"மதுரை தமிழ்\" works, and the choice is per number")
        void tamilCommandAndPerNumber() throws Exception {
            String chooser = "whatsapp:+10000000882";
            String other = "whatsapp:+10000000883";

            assertAccepted(postText(chooser, "மதுரை தமிழ்"));
            assertAccepted(postText(chooser, "நெல் கேள்வி"));
            assertAccepted(postText(other, "நெல் கேள்வி"));

            verify(aiService).getAnswer(anyString(), eq("tamil"), eq("MADURAI"), anyList());
            verify(aiService).getAnswer(anyString(), eq("tamil"), eq("STANDARD"), anyList());   // the other number is still detected
            verify(dialectService, times(1)).detect(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("A question that merely mentions Chennai is not a command")
        void questionIsNotCommand() throws Exception {
            String number = "whatsapp:+10000000884";
            assertAccepted(postText(number, "சென்னை தமிழ் பற்றி சொல்லுங்கள்"));
            verify(dialectService).detect(anyString(), eq("tamil"), anyString());
        }
    }

    @Nested
    @DisplayName("Conversation memory")
    class ConversationMemoryTests {

        private final String number = "whatsapp:+10000000777";   // own number: the controller (and its memory) is shared between tests

        @BeforeEach
        void stubs() {
            when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
        }

        @SuppressWarnings("unchecked")
        private List<List<ChatTurn>> historiesSent(int calls) {
            org.mockito.ArgumentCaptor<List<ChatTurn>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
            verify(aiService, times(calls)).getAnswer(anyString(), anyString(), anyString(), captor.capture());
            return captor.getAllValues();
        }

        @Test
        @DisplayName("A follow-up reaches the AI with the earlier question and answer as history")
        void followUpCarriesHistory() throws Exception {
            when(aiService.getAnswer(anyString(), anyString(), anyString(), anyList()))
                    .thenReturn("Which growth stage?", "Check for whiteflies.");

            assertAccepted(postText(number, "tomato leaf curl"));
            assertAccepted(postText(number, "the leaves are curling and there are white flies"));

            List<List<ChatTurn>> sent = historiesSent(2);
            org.junit.jupiter.api.Assertions.assertTrue(sent.get(0).isEmpty());
            org.junit.jupiter.api.Assertions.assertEquals(
                    List.of(new ChatTurn("user", "tomato leaf curl"), new ChatTurn("assistant", "Which growth stage?")),
                    sent.get(1));
        }

        @Test
        @DisplayName("Greetings and sample/fallback answers are not stored")
        void greetingsAndFallbacksNotStored() throws Exception {
            String other = "whatsapp:+10000000778";
            when(aiService.getAnswer(anyString(), anyString(), anyString(), anyList()))
                    .thenReturn("Hello! Ask me about your crop.",
                            com.gramsetu.service.AIAdvisorService.BUSY_NOTE_TAMIL + "\n\nsample", "real answer");

            assertAccepted(postText(other, "Hello"));            // greeting
            assertAccepted(postText(other, "tomato leaf curl")); // answered with a fallback
            assertAccepted(postText(other, "tomato again"));

            List<List<ChatTurn>> sent = historiesSent(3);
            org.junit.jupiter.api.Assertions.assertTrue(sent.get(1).isEmpty(), "greeting was not stored");
            org.junit.jupiter.api.Assertions.assertTrue(sent.get(2).isEmpty(), "fallback answer was not stored");
        }

        @Test
        @DisplayName("\"reset\", \"புதிதாக\" and \"புது கேள்வி\" clear the history and confirm in Tamil, without an AI call")
        void resetClearsHistory() throws Exception {
            String other = "whatsapp:+10000000779";
            when(aiService.getAnswer(anyString(), anyString(), anyString(), anyList())).thenReturn("answer");

            for (String keyword : List.of("reset", "புதிதாக", "புது கேள்வி")) {
                assertAccepted(postText(other, "tomato leaf curl"));
                assertAccepted(postText(other, keyword));
            }
            assertAccepted(postText(other, "paddy question"));

            verify(messageSender, times(3)).send(eq(other), contains("புதிதாகத் தொடங்கலாம்"), isNull());
            List<List<ChatTurn>> sent = historiesSent(4);   // 3 questions before a reset + 1 after; reset itself makes no AI call
            for (List<ChatTurn> h : sent) org.junit.jupiter.api.Assertions.assertTrue(h.isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. Error handling: services fail, the farmer still gets an answer
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("AIAdvisorService throws → 200 and a generic apology")
        void aiServiceThrows_returns200WithApology() throws Exception {
            when(dialectService.detect(anyString(), anyString(), anyString())).thenReturn(DialectResult.standard());
            when(aiService.getAnswer(anyString(), anyString(), anyString(), anyList()))
                .thenThrow(new RuntimeException("Gemini quota exceeded"));

            assertAccepted(postText(FARMER_NUMBER, "நெல்லுக்கு உரம் என்ன?"));

            verifyTextSent(FARMER_NUMBER, APOLOGY);
        }

        @Test
        @DisplayName("DialectDetectionService throws → 200 and a generic apology")
        void dialectServiceThrows_returns200WithApology() throws Exception {
            when(dialectService.detect(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("detector failure"));

            assertAccepted(postText(FARMER_NUMBER, "நெல்லுக்கு உரம் என்ன?"));

            verifyTextSent(FARMER_NUMBER, APOLOGY);
        }

        @Test
        @DisplayName("CropVisionService throws after a successful download → 200 and a generic apology")
        void cropVisionThrows_afterDownload_returns200WithApology() throws Exception {
            when(restTemplate.exchange(eq(IMAGE_URL), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(FAKE_IMAGE));
            when(cropVisionService.analyzeCropImage(any(), anyString()))
                .thenThrow(new RuntimeException("Gemini vision timeout"));

            assertAccepted(postMedia(FARMER_NUMBER, IMAGE_URL, "image/jpeg"));

            verifyTextSent(FARMER_NUMBER, APOLOGY);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Health check endpoint
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/whatsapp/health (public: verdict only)")
    class PublicHealthTests {

        private void healthy() {
            when(sarvamService.isKeyConfigured()).thenReturn(true);
            when(aiService.isKeyConfigured()).thenReturn(true);
            when(farmerQueryRepo.count()).thenReturn(42L);
            when(cropAdvisoryRepo.count()).thenReturn(15L);
            when(schemeRepo.count()).thenReturn(10L);
        }

        @Test
        @DisplayName("Healthy → 200 and exactly {\"status\":\"UP\"}")
        void healthy_returnsOnlyStatusUp() throws Exception {
            healthy();

            mockMvc.perform(get("/api/whatsapp/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"status\":\"UP\"}", true))   // strict: no other field
                .andExpect(jsonPath("$.length()", is(1)));
        }

        @Test
        @DisplayName("Missing optional API keys are not revealed: still 200 {\"status\":\"UP\"}")
        void missingKeys_areNotRevealed() throws Exception {
            when(sarvamService.isKeyConfigured()).thenReturn(false);
            when(aiService.isKeyConfigured()).thenReturn(false);

            mockMvc.perform(get("/api/whatsapp/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true))
                .andExpect(content().string(not(containsString("keyConfigured"))))
                .andExpect(content().string(not(containsString("DEGRADED"))));
        }

        @Test
        @DisplayName("No components, row counts, identifiers or credentials in the public response")
        void publicResponse_revealsNoDetails() throws Exception {
            healthy();

            mockMvc.perform(get("/api/whatsapp/health"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(content().string(not(containsString("farmerQueries"))))
                .andExpect(content().string(not(containsString("whatsapp:"))))
                .andExpect(content().string(not(containsString("ACtest"))))
                .andExpect(content().string(not(containsString("testtoken"))))
                .andExpect(content().string(not(containsString("gemini"))));
        }

        @Test
        @DisplayName("Database unreachable → 503 and exactly {\"status\":\"DOWN\"} (orchestrators can act on it)")
        void databaseDown_returns503Down() throws Exception {
            healthy();
            when(farmerQueryRepo.count()).thenThrow(new RuntimeException("MySQL: Communications link failure"));

            mockMvc.perform(get("/api/whatsapp/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}", true))
                .andExpect(content().string(not(containsString("MySQL"))));   // the cause is not leaked
        }

        @Test
        @DisplayName("Twilio misconfigured → 503 {\"status\":\"DOWN\"}")
        void twilioMisconfigured_returns503Down() throws Exception {
            healthy();
            when(twilioConfig.getAccountSid()).thenReturn("INVALID-SID");

            mockMvc.perform(get("/api/whatsapp/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}", true));
        }

        @Test
        @DisplayName("Needs no authentication, headers or parameters (what Docker and Railway send)")
        void plainGetIsEnough() throws Exception {
            healthy();

            mockMvc.perform(get("/api/whatsapp/health").header("User-Agent", "Railway-Healthcheck"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/whatsapp/health (detailed report, dev profile)")
    class DetailedHealthTests {

        @BeforeEach
        void turnDetailedReportOn() {
            ReflectionTestUtils.setField(webhookController, "detailedHealth", true);
        }

        @AfterEach
        void turnDetailedReportOff() {
            ReflectionTestUtils.setField(webhookController, "detailedHealth", false);
        }

        @Test
        @DisplayName("All components UP → 200 with status=UP and the model the code really calls")
        void allComponentsUp_returns200() throws Exception {
            when(sarvamService.isKeyConfigured()).thenReturn(true);
            when(aiService.isKeyConfigured()).thenReturn(true);
            when(aiService.getGeminiModel()).thenReturn("test-model");
            when(farmerQueryRepo.count()).thenReturn(42L);
            when(cropAdvisoryRepo.count()).thenReturn(15L);
            when(schemeRepo.count()).thenReturn(10L);

            mockMvc.perform(get("/api/whatsapp/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status",                          is("UP")))
                .andExpect(jsonPath("$.service",                         is("GramSetu WhatsApp Integration")))
                .andExpect(jsonPath("$.components.twilio.status",        is("UP")))
                .andExpect(jsonPath("$.components.sarvam.status",        is("UP")))
                .andExpect(jsonPath("$.components.sarvam.keyConfigured", is(true)))
                .andExpect(jsonPath("$.components.gemini.status",        is("UP")))
                .andExpect(jsonPath("$.components.gemini.model",         is("test-model")))
                .andExpect(jsonPath("$.components.database.status",      is("UP")))
                .andExpect(jsonPath("$.components.database.farmerQueries",     is(42)))
                .andExpect(jsonPath("$.components.database.cropAdvisories",    is(15)))
                .andExpect(jsonPath("$.components.database.governmentSchemes", is(10)))
                .andExpect(jsonPath("$.components.services.status",      is("UP")))
                .andExpect(jsonPath("$.components.services.aiAdvisorService",    is("INITIALIZED")))
                .andExpect(jsonPath("$.components.services.sarvamSpeechService", is("INITIALIZED")));
        }

        @Test
        @DisplayName("The health response never contains the auth token or the full account SID")
        void healthDoesNotLeakCredentials() throws Exception {
            when(sarvamService.isKeyConfigured()).thenReturn(true);
            when(aiService.isKeyConfigured()).thenReturn(true);

            mockMvc.perform(get("/api/whatsapp/health"))
                .andExpect(content().string(not(containsString("testtoken12345678901234567890abcd"))))
                .andExpect(content().string(not(containsString("ACtest1234567890abcdef1234567890ab"))));
        }

        @Test
        @DisplayName("Sarvam key missing → 200 with status=DEGRADED")
        void sarvamKeyMissing_returnsDegraded() throws Exception {
            when(sarvamService.isKeyConfigured()).thenReturn(false);
            when(aiService.isKeyConfigured()).thenReturn(true);

            mockMvc.perform(get("/api/whatsapp/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status",                          is("DEGRADED")))
                .andExpect(jsonPath("$.components.sarvam.status",        is("DEGRADED")))
                .andExpect(jsonPath("$.components.sarvam.keyConfigured", is(false)));
        }

        @Test
        @DisplayName("Both Sarvam and Gemini keys missing → 200 with status=DEGRADED")
        void bothApiKeysMissing_returnsDegraded() throws Exception {
            when(sarvamService.isKeyConfigured()).thenReturn(false);
            when(aiService.isKeyConfigured()).thenReturn(false);

            mockMvc.perform(get("/api/whatsapp/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status",                   is("DEGRADED")))
                .andExpect(jsonPath("$.components.sarvam.status", is("DEGRADED")))
                .andExpect(jsonPath("$.components.gemini.status", is("DEGRADED")));
        }

        @Test
        @DisplayName("Database unreachable → 503 with status=DOWN")
        void dbUnreachable_returns503Down() throws Exception {
            when(sarvamService.isKeyConfigured()).thenReturn(true);
            when(aiService.isKeyConfigured()).thenReturn(true);
            when(farmerQueryRepo.count()).thenThrow(new RuntimeException("MySQL: Communications link failure"));

            mockMvc.perform(get("/api/whatsapp/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status",                     is("DOWN")))
                .andExpect(jsonPath("$.components.database.status", is("DOWN")));
        }

        @Test
        @DisplayName("Twilio SID in the wrong format → 503 with Twilio DOWN")
        void twilioSidWrongFormat_returnsDown() throws Exception {
            when(twilioConfig.getAccountSid()).thenReturn("INVALID-SID");   // not AC + 32 characters
            when(sarvamService.isKeyConfigured()).thenReturn(true);
            when(aiService.isKeyConfigured()).thenReturn(true);

            mockMvc.perform(get("/api/whatsapp/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status",                   is("DOWN")))
                .andExpect(jsonPath("$.components.twilio.status", is("DOWN")));
        }
    }
}
