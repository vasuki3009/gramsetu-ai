package com.gramsetu.controller;

import com.gramsetu.util.LogSafe;
import com.gramsetu.service.AIAdvisorService;
import com.gramsetu.util.InputSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/crops/calendar")
@Slf4j
public class CropCalendarController {

    @Autowired
    private AIAdvisorService gemini;

    @PostMapping("/plan")
    public ResponseEntity<Map<String, String>> getPersonalizedPlan(@RequestBody Map<String, String> body) {
        try {
            String cropName  = body.getOrDefault("cropName",  "தக்காளி");
            String soilType  = body.getOrDefault("soilType",  "செம்மண்");
            String acres     = body.getOrDefault("acres",     "1");
            String season    = body.getOrDefault("season",    "கோடை");
            String language  = InputSanitizer.normalizeLanguage(body.get("language"));

            // These values are inserted into an LLM prompt: bound their size and shape.
            if (InputSanitizer.tooLong(cropName, 60) || InputSanitizer.tooLong(soilType, 60)
                    || InputSanitizer.tooLong(season, 60) || !acres.matches("\\d{1,4}(\\.\\d{1,2})?")) {
                return ResponseEntity.badRequest().body(Map.of("plan",
                    "உள்ளீடு தவறானது. பயிர், மண், பருவம் 60 எழுத்துகளுக்குள் இருக்க வேண்டும்; ஏக்கர் ஒரு எண்ணாக இருக்க வேண்டும்."));
            }

            String prompt = "நான் ஒரு தமிழ்நாடு விவசாயி. என் விவரங்கள்:\n" +
                    "பயிர்: " + cropName + "\n" +
                    "மண் வகை: " + soilType + "\n" +
                    "நிலம்: " + acres + " ஏக்கர்\n" +
                    "பருவம்: " + season + "\n\n" +
                    "என் நிலத்திற்கு ஏற்ற மாத-வாரி விரிவான சாகுபடி திட்டம் தமிழில் கொடுக்கவும்:\n" +
                    "1. எந்த மாதம் விதைக்க வேண்டும் (விதை நேர்த்தி முறையுடன்)\n" +
                    "2. எந்த மாதம் எவ்வளவு தண்ணீர் பாய்ச்ச வேண்டும்\n" +
                    "3. மாதம்-வாரியான உர அட்டவணை (" + acres + " ஏக்கருக்கு கிலோ அளவுகளுடன்)\n" +
                    "4. முக்கிய நோய்கள் வரும் மாதங்கள் + தடுப்பு மருந்துகள்\n" +
                    "5. அறுவடை மாதம் + சராசரி விளைச்சல் அளவு\n\n" +
                    "கட்டாயம்: பதில் தமிழில் மட்டுமே. கிருஷ்ணகிரி / ஹோசூர் பகுதி " + soilType + " மண்ணிற்கு பொருந்தும் வகையில் இருக்க வேண்டும்.";

            // The crop, soil and season are typed by the farmer: log only that a plan is being made, not their values.
            log.info("Generating personalized crop plan");
            String plan = gemini.callGemini(prompt);
            return ResponseEntity.ok(Map.of("plan", plan));

        } catch (Exception e) {
            log.error("Personalized plan error: {}", LogSafe.error(e));
            return ResponseEntity.ok(Map.of("plan",
                "திட்டம் தயாரிக்க இயலவில்லை. மீண்டும் முயற்சிக்கவும்."));
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getCalendar() {
        int month = LocalDate.now().getMonthValue(); // 1=Jan ... 12=Dec
        log.debug("Crop calendar requested for month {}", month);

        List<Map<String, Object>> calendar = new ArrayList<>();
        calendar.add(buildCrop("tomato",    "தக்காளி",      "Tomato",     "🍅",
                List.of("ஜூன்", "ஜூலை", "நவம்பர்", "டிசம்பர்"),
                List.of("செப்டம்பர்", "அக்டோபர்", "பிப்ரவரி", "மார்ச்"),
                "நடவுக்கு முன் கார்பெண்டாசிம் கரைசலில் வேர்களை 30 நிமிடம் நனைக்கவும்.",
                month));

        calendar.add(buildCrop("groundnut", "நிலக்கடலை",   "Groundnut",  "🥜",
                List.of("ஜூன்", "ஜூலை"),
                List.of("அக்டோபர்", "நவம்பர்"),
                "விதைக்கும் போது ஜிப்சம் 200 கிலோ/ஏக்கர் இடவும் — கிழங்கு ஆழம் அதிகரிக்கும்.",
                month));

        calendar.add(buildCrop("turmeric",  "மஞ்சள்",      "Turmeric",   "🌿",
                List.of("மே", "ஜூன்"),
                List.of("ஜனவரி", "பிப்ரவரி"),
                "நடவுக்கு முன் கிழங்கை 30 நிமிடம் மஞ்சள் தண்ணீரில் ஊற வையுங்கள்.",
                month));

        calendar.add(buildCrop("mango",     "மாம்பழம்",    "Mango",      "🥭",
                List.of(),
                List.of("ஏப்ரல்", "மே", "ஜூன்"),
                "பூக்கும் தருணத்தில் பூச்சிக்கொல்லி தெளிக்காதீர்கள் — தேனீக்கள் பாதிக்கும்.",
                month));

        calendar.add(buildCrop("brinjal",   "கத்திரிக்காய்", "Brinjal",  "🍆",
                List.of("ஜூன்", "ஜூலை", "நவம்பர்", "டிசம்பர்"),
                List.of(),
                "15 நாட்களுக்கு ஒருமுறை நீம் ஆயில் தெளிக்கவும் — தண்டு துளைப்பான் தடுக்கும்.",
                month));

        calendar.add(buildCrop("beans",     "பீன்ஸ்",       "Beans",      "🫘",
                List.of("ஜூலை", "ஆகஸ்ட்"),
                List.of("அக்டோபர்", "நவம்பர்"),
                "நடவுக்கு முன் ரைசோபியம் கல்சர் பூசிய விதைகளை மட்டும் பயன்படுத்தவும்.",
                month));

        return ResponseEntity.ok(calendar);
    }

    private Map<String, Object> buildCrop(String key, String nameTamil, String nameEnglish,
                                          String emoji, List<String> plantingMonths,
                                          List<String> harvestingMonths, String localTip,
                                          int currentMonth) {
        String[] activityAndType = getMonthActivity(key, currentMonth);
        return Map.of(
                "cropNameTamil",    nameTamil,
                "cropNameEnglish",  nameEnglish,
                "emoji",            emoji,
                "currentMonthActivity", activityAndType[0],
                "activityType",     activityAndType[1],
                "plantingMonths",   plantingMonths,
                "harvestingMonths", harvestingMonths,
                "localTip",         localTip
        );
    }

    /**
     * Returns [activityDescription (Tamil), activityType] for the given crop and month.
     * activityType: PLANTING | HARVESTING | FLOWERING | MAINTENANCE | IDLE
     */
    private String[] getMonthActivity(String cropKey, int month) {
        return switch (cropKey) {
            case "tomato" -> switch (month) {
                case 6, 7         -> new String[]{"நடவு செய்யுங்கள் (சீசன் 1)", "PLANTING"};
                case 9, 10        -> new String[]{"அறுவடை செய்யுங்கள் (சீசன் 1)", "HARVESTING"};
                case 11, 12       -> new String[]{"நடவு செய்யுங்கள் (சீசன் 2)", "PLANTING"};
                case 1, 2, 3      -> new String[]{"அறுவடை செய்யுங்கள் (சீசன் 2)", "HARVESTING"};
                default           -> new String[]{"இந்த மாதம் ஓய்வு கொடுக்கவும்", "IDLE"};
            };
            case "groundnut" -> switch (month) {
                case 6, 7         -> new String[]{"நடவு செய்யுங்கள்", "PLANTING"};
                case 10, 11       -> new String[]{"அறுவடை செய்யுங்கள்", "HARVESTING"};
                case 8, 9         -> new String[]{"பராமரிப்பு — களை எடுக்கவும்", "MAINTENANCE"};
                default           -> new String[]{"இந்த மாதம் ஓய்வு கொடுக்கவும்", "IDLE"};
            };
            case "turmeric" -> switch (month) {
                case 5, 6         -> new String[]{"நடவு செய்யுங்கள்", "PLANTING"};
                case 1, 2         -> new String[]{"அறுவடை செய்யுங்கள்", "HARVESTING"};
                case 7, 8, 9, 10,
                     11, 12       -> new String[]{"பாசனம் + உரமிடல் பார்க்கவும்", "MAINTENANCE"};
                default           -> new String[]{"இந்த மாதம் ஓய்வு கொடுக்கவும்", "IDLE"};
            };
            case "mango" -> switch (month) {
                case 1, 2         -> new String[]{"பூக்கும் காலம் — பூச்சிக்கொல்லி வேண்டாம்", "FLOWERING"};
                case 4, 5, 6      -> new String[]{"அறுவடை செய்யுங்கள்", "HARVESTING"};
                case 3            -> new String[]{"காய் கட்டும் காலம் — பராமரிக்கவும்", "MAINTENANCE"};
                default           -> new String[]{"கிளை கத்தரிப்பு செய்யுங்கள்", "MAINTENANCE"};
            };
            case "brinjal" -> switch (month) {
                case 6, 7, 11, 12 -> new String[]{"நடவுக்கு சிறந்த தருணம்", "PLANTING"};
                default           -> new String[]{"நடவு செய்யலாம் — பராமரிக்கவும்", "MAINTENANCE"};
            };
            case "beans" -> switch (month) {
                case 7, 8         -> new String[]{"நடவு செய்யுங்கள்", "PLANTING"};
                case 10, 11       -> new String[]{"அறுவடை செய்யுங்கள்", "HARVESTING"};
                case 9            -> new String[]{"பராமரிப்பு — தண்ணீர் பாய்ச்சவும்", "MAINTENANCE"};
                default           -> new String[]{"இந்த மாதம் ஓய்வு கொடுக்கவும்", "IDLE"};
            };
            default -> new String[]{"தகவல் இல்லை", "IDLE"};
        };
    }
}
