package com.gramsetu.controller;

import com.gramsetu.util.LogSafe;
import com.gramsetu.dto.CropResponse;
import com.gramsetu.dto.DialectResult;
import com.gramsetu.util.InputSanitizer;
import org.springframework.http.HttpStatus;
import com.gramsetu.model.CropAdvisory;
import com.gramsetu.model.FarmerQuery;
import com.gramsetu.repository.CropAdvisoryRepository;
import com.gramsetu.repository.FarmerQueryRepository;
import com.gramsetu.service.CropVisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/crop")
@Slf4j
@RequiredArgsConstructor
public class CropAnalysisController {

    private final CropVisionService cropVisionService;
    private final FarmerQueryRepository farmerQueryRepo;
    private final CropAdvisoryRepository cropAdvisoryRepo;

    @PostMapping("/analyze")
    public ResponseEntity<CropResponse> analyzeCrop(
            @RequestParam MultipartFile image,
            @RequestParam(required = false, defaultValue = "tamil") String language) {
        language = InputSanitizer.normalizeLanguage(language);
        String contentType = image.getContentType();
        if (image.isEmpty() || contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            String msg = "english".equals(language)
                    ? "Please upload an image file."
                    : "படக் கோப்பை பதிவேற்றவும்.";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new CropResponse(msg, false, "", ""));
        }
        try {
            CropResponse result = cropVisionService.analyzeCropImage(image.getBytes(), language);

            FarmerQuery query = new FarmerQuery(
                    "Crop image analysis",
                    result.getDiagnosis(),
                    DialectResult.standard(),
                    "Unknown"
            );
            farmerQueryRepo.save(query);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Crop analysis error: {}", LogSafe.error(e));
            String errorMessage = "english".equals(language)
                    ? "Image analysis failed. Please try again."
                    : "படம் பகுப்பாய்வு தோல்வியடைந்தது. மீண்டும் முயற்சிக்கவும்.";
            return ResponseEntity.ok(new CropResponse(
                    errorMessage,
                    false,
                    "AI பரிந்துரை",
                    ""
            ));
        }
    }

    @GetMapping("/advisories")
    public ResponseEntity<List<CropAdvisory>> getAllAdvisories() {
        return ResponseEntity.ok(cropAdvisoryRepo.findAll());
    }
}
