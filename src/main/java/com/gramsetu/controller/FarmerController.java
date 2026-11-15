package com.gramsetu.controller;

import com.gramsetu.util.LogSafe;
import com.gramsetu.dto.SchemeResponse;
import com.gramsetu.dto.VoiceResponse;
import com.gramsetu.model.FarmerQuery;
import com.gramsetu.repository.FarmerQueryRepository;
import com.gramsetu.service.AIAdvisorService;
import com.gramsetu.service.DialectDetectionService;
import com.gramsetu.service.SarvamSpeechService;
import com.gramsetu.service.SchemeRecommendationService;
import com.gramsetu.dto.DialectResult;
import com.gramsetu.util.AnswerLanguage;
import com.gramsetu.util.InputSanitizer;
import com.gramsetu.util.SchemeIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/farmer")
@Slf4j
@RequiredArgsConstructor
public class FarmerController {

    private final SarvamSpeechService sarvamService;
    private final AIAdvisorService aiService;
    private final DialectDetectionService dialectService;
    private final FarmerQueryRepository farmerQueryRepo;
    private final SchemeRecommendationService schemeService;

    @Value("${app.limits.max-question-length:1000}")
    private int maxQuestionLength;

    @Value("${app.limits.max-audio-bytes:2097152}")
    private long maxAudioBytes;

    private static VoiceResponse rejection(String language, String message) {
        return new VoiceResponse(message, "", "STANDARD", "Tamil Nadu", 0.0, "FALLBACK",
                List.of(), "", "", language);
    }

    /** Server speech in the answer's language: English voice for English, the dialect voice for Tamil. "" on failure. */
    private String speak(String answer, DialectResult dialect, String answerLanguage) {
        return "english".equals(answerLanguage)
                ? sarvamService.toEnglishAudio(answer)
                : sarvamService.toAudio(answer, dialect.getDialect());
    }

    @PostMapping("/voice")
    public ResponseEntity<VoiceResponse> handleVoice(
            @RequestParam MultipartFile audio,
            @RequestParam(required = false, defaultValue = "Hosur") String location,
            @RequestParam(required = false, defaultValue = "tamil") String language) {
        language = InputSanitizer.normalizeLanguage(language);
        location = InputSanitizer.limitLocation(location, "Hosur");
        if (audio.isEmpty()) {
            return ResponseEntity.badRequest().body(rejection(language, "english".equals(language)
                    ? "No audio received." : "குரல் பதிவு எதுவும் கிடைக்கவில்லை."));
        }
        if (audio.getSize() > maxAudioBytes) {
            log.warn("Voice upload rejected: {} bytes exceeds limit {}", audio.getSize(), maxAudioBytes);
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(rejection(language, "english".equals(language)
                    ? "Audio recording is too large. Please send a shorter one."
                    : "குரல் பதிவு மிகப் பெரியது. சிறிய பதிவை அனுப்பவும்."));
        }
        try {
            byte[] audioBytes = audio.getBytes();

            // English mode lets Sarvam detect the spoken language; Tamil mode keeps Tamil.
            String transcript = "english".equals(language)
                    ? sarvamService.toTextAutoDetect(audioBytes, audio.getContentType())
                    : sarvamService.toText(audioBytes, audio.getContentType());
            log.info("STT result: {} chars", LogSafe.len(transcript));

            // The answer is English if the button is English or the question is clearly English; Tamil otherwise.
            String answerLanguage = AnswerLanguage.forWeb(language, transcript);

            DialectResult dialect = dialectService.detect(transcript, answerLanguage, location);
            log.info("Dialect detected: {} ({}), answer language {}", dialect.getDialect(), dialect.getDetectedBy(), answerLanguage);

            String prompt = dialectService.buildAnswerPrompt(transcript, dialect, answerLanguage);
            String answer = aiService.getAnswer(prompt, answerLanguage, dialect.getDialect());
            log.info("Gemini answer length: {}", answer.length());

            // Scheme matching — only for questions about schemes, subsidies, loans, insurance (never for a
            // greeting or an ordinary farming question); never crashes the main flow.
            List<SchemeResponse> schemes = List.of();
            if (SchemeIntent.isSchemeQuestion(transcript)) {
                try {
                    schemes = schemeService.findMatchingSchemes(transcript, answerLanguage);
                } catch (Exception e) {
                    log.warn("Scheme matching failed, skipping: {}", LogSafe.error(e));
                }
            }

            // Append scheme names to the answer, under a heading in the answer's language
            String combinedAnswer = SchemeIntent.appendSchemes(answer, schemes, answerLanguage);

            // Speak the answer only: the scheme list under it is for reading, and would just make the audio longer.
            String audioBase64 = speak(answer, dialect, answerLanguage);
            farmerQueryRepo.save(new FarmerQuery(transcript, combinedAnswer, dialect, location));

            return ResponseEntity.ok(new VoiceResponse(
                    combinedAnswer, audioBase64,
                    dialect.getDialect(), dialect.getDialectRegion(),
                    dialect.getConfidence(), dialect.getDetectedBy(),
                    schemes, "", transcript, answerLanguage));

        } catch (Exception e) {
            log.error("Voice endpoint error: {}", LogSafe.error(e));
            String errorMessage = "english".equals(language)
                    ? "Connection error. Please try again."
                    : "இணைப்பு பிழை. மீண்டும் முயற்சிக்கவும்.";
            return ResponseEntity.ok(new VoiceResponse(
                    errorMessage, "",
                    "STANDARD", "Tamil Nadu", 0.0, "FALLBACK",
                    List.of(), "", "", language));
        }
    }

    @PostMapping("/ask")
    public ResponseEntity<VoiceResponse> handleTextQuestion(
            @RequestBody Map<String, String> body) {
        try {
            String question = body.get("question");
            String location = InputSanitizer.limitLocation(body.get("location"), "Hosur");
            String language = InputSanitizer.normalizeLanguage(body.get("language"));

            if (question == null || question.trim().isEmpty()) {
                return ResponseEntity.ok(new VoiceResponse(
                        "english".equals(language) ? "The question is empty." : "கேள்வி காலியாக உள்ளது", "",
                        "STANDARD", "Tamil Nadu", 0.0, "FALLBACK",
                        List.of(), "", "", language));
            }

            if (InputSanitizer.tooLong(question, maxQuestionLength)) {
                return ResponseEntity.badRequest().body(rejection(language, "english".equals(language)
                        ? "Your question is too long (maximum " + maxQuestionLength + " characters)."
                        : "கேள்வி மிக நீளமாக உள்ளது. " + maxQuestionLength + " எழுத்துகளுக்குள் கேளுங்கள்."));
            }

            // The answer is English if the button is English or the question is clearly English; Tamil otherwise.
            String answerLanguage = AnswerLanguage.forWeb(language, question);

            DialectResult dialect = dialectService.detect(question, answerLanguage, location);
            log.info("Text question dialect: {} ({}), answer language {}", dialect.getDialect(), dialect.getDetectedBy(), answerLanguage);

            String prompt = dialectService.buildAnswerPrompt(question, dialect, answerLanguage);
            String answer = aiService.getAnswer(prompt, answerLanguage, dialect.getDialect());

            String audioBase64 = speak(answer, dialect, answerLanguage);
            farmerQueryRepo.save(new FarmerQuery(question, answer, dialect, location));

            return ResponseEntity.ok(new VoiceResponse(
                    answer, audioBase64,
                    dialect.getDialect(), dialect.getDialectRegion(),
                    dialect.getConfidence(), dialect.getDetectedBy(),
                    List.of(), "", question, answerLanguage));

        } catch (Exception e) {
            log.error("Ask endpoint error: {}", LogSafe.error(e));
            String errorMessage = "english".equals(InputSanitizer.normalizeLanguage(body.get("language")))
                    ? "Connection error. Please try again."
                    : "இணைப்பு பிழை. மீண்டும் முயற்சிக்கவும்.";
            return ResponseEntity.ok(new VoiceResponse(
                    errorMessage, "",
                    "STANDARD", "Tamil Nadu", 0.0, "FALLBACK",
                    List.of(), "", "", InputSanitizer.normalizeLanguage(body.get("language"))));
        }
    }
}
