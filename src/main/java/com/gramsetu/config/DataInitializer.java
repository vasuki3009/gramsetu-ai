package com.gramsetu.config;

import com.gramsetu.model.GovernmentScheme;
import com.gramsetu.repository.GovernmentSchemeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the government_schemes table on every startup if it is empty.
 * Uses JPA (ApplicationRunner) instead of data.sql so it works regardless
 * of spring.sql.init.mode, and after Hibernate has created/updated the schema.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final GovernmentSchemeRepository schemeRepo;

    @Override
    public void run(ApplicationArguments args) {
        if (schemeRepo.count() > 0) {
            // Re-seed if names are still English-only (no Tamil bilingual format yet)
            boolean needsUpdate = schemeRepo.findAll().stream()
                    .anyMatch(s -> !s.getSchemeName().matches(".*[\\u0B80-\\u0BFF].*"));
            if (!needsUpdate) {
                log.info("government_schemes already bilingual ({} rows) — skipping", schemeRepo.count());
                return;
            }
            log.info("Re-seeding government schemes with bilingual Tamil names...");
            schemeRepo.deleteAll();
        }
        log.info("Seeding government schemes...");
        schemeRepo.saveAll(buildSchemes());
        log.info("Seeded {} government schemes", schemeRepo.count());
    }

    private List<GovernmentScheme> buildSchemes() {
        return List.of(
            scheme(
                "பி.எம் கிசான் சம்மான் நிதி (PM Kisan Samman Nidhi)",
                "சிறு மற்றும் குறு விவசாயிகள் — 2 ஹெக்டேர் (சுமார் 5 ஏக்கர்) வரை நிலம் உள்ளவர்கள்",
                "ஆண்டுக்கு ₹6,000 — ₹2,000 வீதம் 3 தவணைகளில் (மூன்று முறை) நேரடியாக வங்கி கணக்கில்",
                "https://pmkisan.gov.in"
            ),
            scheme(
                "பி.எம் பயிர் காப்பீட்டு திட்டம் (PM Fasal Bima Yojana)",
                "பயிர் கடன் பெற்ற அல்லது தன்னார்வமாக சேரும் அனைத்து விவசாயிகளும்",
                "இயற்கை பேரிடர், பூச்சி தாக்குதல், நோயால் பயிர் சேதமடைந்தால் காப்பீடு கிடைக்கும். கார் பருவ (ஜூன்–செப்டம்பர்) பயிருக்கு காப்பீட்டு சந்தா வெறும் 2% மட்டுமே",
                "https://pmfby.gov.in"
            ),
            scheme(
                "உழவர் பாதுகாப்புத் திட்டம் (Uzhavar Padhukappu Thittam)",
                "தமிழ்நாடு விவசாயி அடையாள அட்டை (விவசாயி ID) உடைய அனைத்து விவசாயிகளும்",
                "விபத்தில் இறந்தால் ₹2 லட்சம் காப்பீடு, ஊனமுற்றால் ₹1 லட்சம், மாதம் ₹1,000 ஓய்வூதியம்",
                "https://www.tn.gov.in/scheme/data_view/11736"
            ),
            scheme(
                "விவசாயிகளுக்கு இலவச மின்சாரம் (Free Electricity Scheme – TN)",
                "தமிழ்நாட்டில் 10 குதிரை திறன் (HP) வரை மோட்டார் பம்ப் பயன்படுத்தும் பதிவுசெய்யப்பட்ட விவசாயிகள்",
                "விவசாய மோட்டார் பம்புகளுக்கு ஆண்டுக்கு 3,600 யூனிட்கள் இலவச மின்சாரம்",
                "https://www.tangedco.gov.in"
            ),
            scheme(
                "மண் நல அட்டை திட்டம் (Soil Health Card Scheme)",
                "இந்தியாவில் உள்ள அனைத்து விவசாயிகளும்",
                "ஒவ்வொரு 2 ஆண்டுகளுக்கும் இலவச மண் பரிசோதனை — எந்த உரம் எவ்வளவு போட வேண்டும் என்று தனிப்பட்ட பரிந்துரை கிடைக்கும்",
                "https://soilhealth.dac.gov.in"
            ),
            scheme(
                "கிசான் ஓய்வூதிய திட்டம் (PM Kisan Maan Dhan Yojana)",
                "18 முதல் 40 வயது வரை உள்ள சிறு மற்றும் குறு விவசாயிகள்",
                "60 வயதிற்கு பிறகு மாதம் ₹3,000 ஓய்வூதியம். மாத சந்தா ₹55 முதல் ₹200 வரை மட்டுமே",
                "https://pmkmy.gov.in"
            ),
            scheme(
                "கிசான் கடன் அட்டை (Kisan Credit Card)",
                "அனைத்து விவசாயிகள், பங்குதாரர்கள் மற்றும் குத்தகை விவசாயிகளும் தகுதியுடையவர்கள்",
                "₹3 லட்சம் வரை வெறும் 4% வட்டியில் குறுகிய கால பயிர் கடன் — அதிக வட்டியிலான கடன் வேண்டாம்",
                "https://pmkisan.gov.in/kcc"
            )
        );
    }

    private GovernmentScheme scheme(String name, String eligibility, String benefits, String link) {
        GovernmentScheme s = new GovernmentScheme();
        s.setSchemeName(name);
        s.setEligibility(eligibility);
        s.setBenefits(benefits);
        s.setOfficialLink(link);
        return s;
    }
}
