package com.gramsetu.service;

import com.gramsetu.util.LogSafe;
import com.gramsetu.dto.SchemeResponse;
import com.gramsetu.model.GovernmentScheme;
import com.gramsetu.repository.GovernmentSchemeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SchemeRecommendationService {

    private final GovernmentSchemeRepository schemeRepo;
    private final AIAdvisorService gemini;

    /**
     * Returns all schemes from DB (up to 6) plus a Gemini-generated Tamil
     * explanation of which ones are most relevant for the farmer's question.
     *
     * Previously this filtered by matching scheme name prefixes inside the
     * Gemini response, which always returned 0 results because Gemini replied
     * in Tamil and never mentioned English scheme names.
     */
    public List<SchemeResponse> findMatchingSchemes(String farmerQuestion, String language) {
        List<GovernmentScheme> allSchemes = schemeRepo.findAll();

        if (allSchemes.isEmpty()) {
            log.warn("government_schemes table is empty — DataInitializer may not have run yet");
            return List.of();
        }

        // Build a compact summary for the Gemini prompt
        String schemeSummary = allSchemes.stream()
                .map(s -> "- " + s.getSchemeName() + ": " + s.getBenefits())
                .collect(Collectors.joining("\n"));

        String prompt = "english".equals(language)
                ? "Farmer question: " + farmerQuestion
                  + "\n\nAvailable government schemes:\n" + schemeSummary
                  + "\n\nIn 2-3 sentences explain which schemes are most relevant for this farmer."
                : "விவசாயி கேள்வி: " + farmerQuestion
                  + "\n\nகிடைக்கும் அரசு திட்டங்கள்:\n" + schemeSummary
                  + "\n\nஒரு சிறிய பத்தியில் (2-3 வாக்கியங்கள்), இந்த விவசாயிக்கு எந்த திட்டங்கள்"
                  + " மிகவும் பொருந்தும் என்று தமிழில் மட்டும் விளக்கவும்.";

        String explanation;
        try {
            explanation = gemini.callGemini(prompt);
            log.info("Scheme explanation generated ({} chars)", explanation.length());
        } catch (Exception e) {
            log.warn("Gemini explanation failed, using default: {}", LogSafe.error(e));
            explanation = "english".equals(language)
                    ? "The following government schemes may be applicable for you:"
                    : "கீழ்க்கண்ட அரசு திட்டங்கள் உங்களுக்கு பொருந்தலாம்:";
        }

        final String finalExplanation = explanation;

        // Return all schemes; tamilExplanation is attached only to first item
        // (SchemeScreen renders schemes[0].tamilExplanation as the summary block)
        List<GovernmentScheme> limited = allSchemes.stream().limit(6).collect(Collectors.toList());

        return limited.stream()
                .map(s -> new SchemeResponse(
                        s.getSchemeName(),
                        s.getEligibility(),
                        s.getBenefits(),
                        s.getOfficialLink(),
                        limited.indexOf(s) == 0 ? finalExplanation : null
                ))
                .collect(Collectors.toList());
    }
}
