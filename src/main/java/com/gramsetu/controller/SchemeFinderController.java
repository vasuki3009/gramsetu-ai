package com.gramsetu.controller;

import com.gramsetu.util.LogSafe;
import com.gramsetu.dto.SchemeResponse;
import com.gramsetu.util.InputSanitizer;
import org.springframework.beans.factory.annotation.Value;
import com.gramsetu.model.GovernmentScheme;
import com.gramsetu.repository.GovernmentSchemeRepository;
import com.gramsetu.service.SchemeRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/schemes")
@Slf4j
@RequiredArgsConstructor
public class SchemeFinderController {

    private final SchemeRecommendationService schemeService;
    private final GovernmentSchemeRepository schemeRepo;

    @Value("${app.limits.max-question-length:1000}")
    private int maxQuestionLength;

    @PostMapping("/find")
    public ResponseEntity<List<SchemeResponse>> findSchemes(@RequestBody Map<String, String> body) {
        try {
            String question = body.get("question");
            String language = InputSanitizer.normalizeLanguage(body.get("language"));
            if (question == null || question.trim().isEmpty()
                    || InputSanitizer.tooLong(question, maxQuestionLength)) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(schemeService.findMatchingSchemes(question, language));

        } catch (Exception e) {
            log.error("Scheme finder error: {}", LogSafe.error(e));
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<GovernmentScheme>> getAllSchemes() {
        return ResponseEntity.ok(schemeRepo.findAll());
    }
}
