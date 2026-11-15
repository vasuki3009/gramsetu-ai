package com.gramsetu.controller;

import com.gramsetu.util.LogSafe;
import com.gramsetu.dto.DialectResult;
import com.gramsetu.dto.VoiceResponse;
import com.gramsetu.service.AIAdvisorService;
import com.gramsetu.service.DialectDetectionService;
import com.gramsetu.service.SarvamSpeechService;
import com.gramsetu.util.DialectPromptBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for the dialect-aware voice processing pipeline.
 *
 * POST /api/voice/process  (multipart/form-data, param: audio)
 *
 * Pipeline:
 *   audio file → STT → dialect detection → prompt build → AI answer → TTS → VoiceResponse
 *
 * Confidence threshold: if dialect confidence < 30 → treat as STANDARD
 * Voices: chosen per dialect in SarvamTtsVoices (bulbul:v3 speakers).
 */
@RestController
@RequestMapping("/api/voice")
@Slf4j
public class VoiceController {

    private static final int  CONFIDENCE_THRESHOLD = 30;
    private static final String LANGUAGE            = "tamil";
    private static final String LOCATION            = "";

    @Value("${app.limits.max-audio-bytes:2097152}")
    private long maxAudioBytes;

    @Autowired
    private SarvamSpeechService sarvamService;

    @Autowired
    private DialectDetectionService dialectService;

    @Autowired
    private AIAdvisorService aiService;

    /**
     * Processes a voice note uploaded as multipart/form-data.
     *
     * @param audio  Audio file (ogg, webm, wav, or mp3) — WhatsApp voice notes are ogg/opus
     * @return       {@link VoiceResponse} with transcript, dialect, AI answer, and TTS audio
     */
    @PostMapping(value = "/process", consumes = "multipart/form-data")
    public ResponseEntity<VoiceResponse> processVoice(
            @RequestParam("audio") MultipartFile audio) {

        log.info("[VoiceController] Received voice upload: size={} contentType={}",
                 audio.getSize(), audio.getContentType());

        if (audio.isEmpty()) {
            return ResponseEntity.badRequest().body(errorResponse("குரல் பதிவு எதுவும் கிடைக்கவில்லை."));
        }
        if (audio.getSize() > maxAudioBytes) {
            log.warn("[VoiceController] Upload rejected: {} bytes exceeds limit {}", audio.getSize(), maxAudioBytes);
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(errorResponse("குரல் பதிவு மிகப் பெரியது. சிறிய பதிவை அனுப்பவும்."));
        }

        // ── Step 1: Speech-to-Text ─────────────────────────────────────────────
        byte[] audioBytes;
        try {
            audioBytes = audio.getBytes();
        } catch (Exception e) {
            log.error("[VoiceController] Failed to read uploaded audio: {}", LogSafe.error(e));
            return ResponseEntity.badRequest().body(errorResponse("குரல் கோப்பை படிக்க முடியவில்லை."));
        }

        String contentType = audio.getContentType() != null ? audio.getContentType() : "audio/ogg";
        String transcript = sarvamService.toText(audioBytes, contentType);
        log.info("[VoiceController] STT transcript: {} chars", LogSafe.len(transcript));

        // ── Step 2: Dialect detection ─────────────────────────────────────────
        DialectResult dialect = detectDialect(transcript);

        // Apply confidence threshold
        if (dialect.getConfidence() < CONFIDENCE_THRESHOLD) {
            log.info("[VoiceController] Confidence {}% < threshold {}% → overriding to STANDARD",
                     dialect.getConfidence(), CONFIDENCE_THRESHOLD);
            dialect = DialectResult.standard();
        }

        log.info("[VoiceController] Dialect: {} region={} confidence={}% via={}",
                 dialect.getDialect(), dialect.getDialectRegion(),
                 dialect.getConfidence(), dialect.getDetectedBy());

        // Log every detection for future training data
        log.info("[DIALECT_TRAINING] transcriptChars={} dialect={} region={} confidence={} detectedBy={}",
                 LogSafe.len(transcript), dialect.getDialect(), dialect.getDialectRegion(),
                 dialect.getConfidence(), dialect.getDetectedBy());

        // ── Step 3: Build dialect-aware Gemini prompt ─────────────────────────
        String prompt = DialectPromptBuilder.buildPrompt(transcript, dialect.getDialect(), null);

        // ── Step 4: AI answer ─────────────────────────────────────────────────
        String aiResponse;
        try {
            aiResponse = aiService.getAnswer(prompt, LANGUAGE, dialect.getDialect());
        } catch (Exception e) {
            log.warn("[VoiceController] AI call failed, using demo answer: {}", LogSafe.error(e));
            aiResponse = aiService.getDemoAnswer(transcript, LANGUAGE);
        }
        log.info("[VoiceController] AI answer length={} chars", aiResponse.length());

        // ── Step 5: Text-to-Speech with dialect-mapped voice ──────────────────
        String audioBase64 = synthesiseSpeech(aiResponse, dialect.getDialect());

        // ── Build response ─────────────────────────────────────────────────────
        VoiceResponse response = new VoiceResponse(
                aiResponse,
                audioBase64,
                dialect.getDialect(),
                dialect.getDialectRegion(),
                dialect.getConfidence(),
                dialect.getDetectedBy(),
                List.of(),
                "",
                transcript
        );

        return ResponseEntity.ok(response);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private DialectResult detectDialect(String transcript) {
        try {
            return dialectService.detect(transcript, LANGUAGE, LOCATION);
        } catch (Exception e) {
            log.warn("[VoiceController] Dialect detection error (fallback to STANDARD): {}", LogSafe.error(e));
            return DialectResult.standard();
        }
    }

    /** Audio for the reply, or "" (text reply only). SarvamSpeechService logs the reason when TTS fails. */
    private String synthesiseSpeech(String text, String dialect) {
        try {
            String audio = sarvamService.toAudio(text, dialect);
            if (audio == null || audio.isBlank()) return "";
            log.info("[VoiceController] TTS OK, audioBase64.length={}", audio.length());
            return audio;
        } catch (Exception e) {
            log.warn("[VoiceController] TTS failed (text reply only): {}", LogSafe.error(e));
            return "";
        }
    }

    private VoiceResponse errorResponse(String message) {
        return new VoiceResponse(
                message, "", "STANDARD", "Tamil Nadu", 0.0, "ERROR",
                List.of(), "", "");
    }
}
