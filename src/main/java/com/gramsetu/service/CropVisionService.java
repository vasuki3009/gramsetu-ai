package com.gramsetu.service;

import com.gramsetu.util.LogSafe;
import com.gramsetu.dto.CropResponse;
import com.gramsetu.model.CropAdvisory;
import com.gramsetu.repository.CropAdvisoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class CropVisionService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Autowired
    private CropAdvisoryRepository advisoryRepo;

    @Autowired
    private SarvamSpeechService sarvamService;

    private RestTemplate restTemplate;

    public CropVisionService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(60_000); // Vision API needs longer — large base64 payload
        this.restTemplate = new RestTemplate(factory);
    }

    /** Same model setting as the text answers (GEMINI_MODEL). */
    @Value("${gemini.model:" + AIAdvisorService.DEFAULT_GEMINI_MODEL + "}")
    private String geminiModel = AIAdvisorService.DEFAULT_GEMINI_MODEL;

    private String geminiUrl() {
        return "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel.trim() + ":generateContent";
    }

    private boolean isKeyConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && !apiKey.toUpperCase().startsWith("YOUR_");
    }

    @SuppressWarnings("unchecked")
    public CropResponse analyzeCropImage(byte[] imageBytes, String language) {
        if (!isKeyConfigured()) {
            log.warn("Gemini API key not configured – returning demo crop response. " +
                     "Set gemini.api.key in application.properties");
            return getDemoCropResponse(language);
        }
        String mimeType = detectMimeType(imageBytes);
        log.debug("Analyzing crop image: {} bytes, MIME: {}", imageBytes.length, mimeType);
        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            String prompt = "english".equals(language) ? """
                    You are an expert agricultural scientist specialising in Tamil Nadu crops
                    (tomato, beans, paddy, ragi, groundnut). Carefully examine this crop image.
                    Respond ONLY in this exact 6-section format in English:

                    Disease: [full disease name with scientific name if known]
                    Symptoms: [2-3 visible symptoms observed in this image]
                    Cause: [root cause — fungus / bacteria / virus / pest / nutrient deficiency]
                    Treatment:
                    1. [immediate action — remove/isolate]
                    2. [chemical or biological treatment with dosage]
                    3. [follow-up action after 7-10 days]
                    Prevention:
                    1. [preventive cultural practice]
                    2. [preventive spray or seed treatment]
                    Medicine: [specific pesticide / fungicide name + dosage available in local agri shop]

                    IMPORTANT: Reply ONLY in the above format. No extra text before or after.
                    """ : """
                    நீங்கள் தமிழ்நாடு வேளாண் விஞ்ஞானி. இந்த பயிர் படத்தை கவனமாக ஆய்வு செய்யவும்.

                    முதலில் இந்த வேறுபாடுகளை கவனமாக பாருங்கள்:
                    - புள்ளிகள், கருப்பு/பழுப்பு திட்டுகள், எரிந்த விளிம்புகள் = பூஞ்சை அல்லது பாக்டீரியா நோய்
                    - சீரற்ற மஞ்சள் நிறம் (வரிகள் இல்லாமல்) = வைரஸ் நோய்
                    - சீரான வெளிர் மஞ்சள் நிறம் (புள்ளிகள் இல்லாமல்) = சத்துக்குறைபாடு
                    - இலையில் துளைகள், கடித்த தடங்கள் = பூச்சி தாக்குதல்
                    - வேர் அருகே கருப்பு அழுகல் = நேரடி நோய்

                    கட்டாயம் கீழ்க்கண்ட 6 பிரிவு வடிவத்தில் தமிழில் மட்டுமே பதில் சொல்லவும்:

                    நோய்: [சரியான நோயின் பெயர் தமிழிலும் ஆங்கிலத்திலும்]
                    அறிகுறிகள்: [இந்த படத்தில் தெரியும் 2-3 குறிப்பிட்ட அறிகுறிகள் — நிறம், வடிவம், இடம்]
                    காரணம்: [மூல காரணம் — பூஞ்சான் / பாக்டீரியா / வைரஸ் / பூச்சி / சத்துக்குறைபாடு என்று தெளிவாக சொல்லவும்]
                    சிகிச்சை:
                    1. [உடனடி நடவடிக்கை]
                    2. [சரியான மருந்து + அளவு — காரணத்திற்கு ஏற்ப: பூஞ்சானுக்கு fungicide, பாக்டீரியாவுக்கு bactericide, சத்துக்குறைபாட்டிற்கு உரம்]
                    3. [7-10 நாட்களில் மேற்கொள்ள வேண்டியது]
                    தடுப்பு முறைகள்:
                    1. [நடைமுறை தடுப்பு]
                    2. [தடுப்பு மருந்து அல்லது இயற்கை முறை]
                    மருந்து: [மருந்தின் பெயர் மட்டும். அளவு எழுத வேண்டாம்; மருந்து டப்பாவில் உள்ள அளவைப் பின்பற்றி, வேளாண் அலுவலகத்தில் உறுதி செய்யவும்]

                    முக்கியம்: படத்தில் புள்ளிகள் அல்லது திட்டுகள் தெரிந்தால் கட்டாயம் நோயாக கண்டறியவும் — சத்துக்குறைபாடாக தவறாக கண்டறியாதீர்கள்.
                    மேற்கண்ட 6 பிரிவு வடிவத்தில் மட்டுமே பதில் சொல்லவும். வேறு எந்த உரையும் வேண்டாம்.
                    """;

            Map<String, Object> textPart = Map.of("text", prompt);
            Map<String, Object> imagePart = Map.of(
                    "inline_data", Map.of(
                            "mime_type", mimeType,
                            "data", base64Image
                    )
            );

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(textPart, imagePart))
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    geminiUrl(),
                    requestEntity,
                    Map.class
            );

            log.debug("Gemini Vision response status: {}", response.getStatusCode());

            String geminiDiagnosis = extractTextFromGeminiResponse(response.getBody(), language);
            log.debug("Gemini crop diagnosis received ({} chars)", geminiDiagnosis.length());

            Optional<CropAdvisory> advisory = findMatchingAdvisory(geminiDiagnosis, language);

            String audioBase64 = "";
            if (!"english".equals(language)) {
                try {
                    audioBase64 = sarvamService.toAudio(geminiDiagnosis, "STANDARD");
                } catch (Exception ttsEx) {
                    log.warn("TTS generation failed for crop diagnosis: {}", LogSafe.error(ttsEx));
                }
            }

            return new CropResponse(
                    geminiDiagnosis,
                    advisory.isPresent(),
                    advisory.map(CropAdvisory::getSource).orElse("AI பரிந்துரை"),
                    audioBase64
            );

        } catch (Exception e) {
            log.error("Crop vision analysis error: {}", LogSafe.error(e));
            return getDemoCropResponse(language);
        }
    }

    /** Detects image MIME type from magic bytes. Defaults to image/jpeg. */
    private String detectMimeType(byte[] imageBytes) {
        if (imageBytes.length >= 4 &&
                imageBytes[0] == (byte) 0x89 && imageBytes[1] == 0x50 &&
                imageBytes[2] == 0x4E && imageBytes[3] == 0x47) {
            return "image/png";
        }
        if (imageBytes.length >= 12 &&
                imageBytes[0] == 0x52 && imageBytes[1] == 0x49 &&
                imageBytes[8] == 0x57 && imageBytes[9] == 0x45) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private CropResponse getDemoCropResponse(String language) {
        if ("english".equals(language)) {
            String diagnosis = "Disease: Blast Disease (Magnaporthe oryzae)\n" +
                    "Symptoms: Brown lesions with grey centres on leaves; spindle-shaped spots on stems.\n" +
                    "Cause: Fungal infection — Magnaporthe oryzae, spread by wind and rain splash.\n" +
                    "Treatment:\n" +
                    "1. Remove and destroy all infected leaves and stems immediately.\n" +
                    "2. Spray Tricyclazole 75 WP at 0.6 g/litre water every 10 days.\n" +
                    "3. After 10 days check for new lesions; repeat spray if needed.\n" +
                    "Prevention:\n" +
                    "1. Use disease-resistant varieties; avoid excessive nitrogen fertiliser.\n" +
                    "2. Treat seeds with Carbendazim 2 g/kg before sowing.\n" +
                    "Medicine: Tricyclazole 75 WP – 0.6 g/litre (available at local agri shops)";
            return new CropResponse(diagnosis, false, "AI Recommendation", "");
        }
        String diagnosis = "நோய்: பிளாஸ்ட் நோய் (Blast Disease — Magnaporthe oryzae)\n" +
                "அறிகுறிகள்: இலைகளில் நடுவில் சாம்பல் நிறமும் விளிம்பில் பழுப்பு நிறமும் கொண்ட " +
                "வெட்டு வடிவ புள்ளிகள்; தண்டில் கருப்பு வளையங்கள்.\n" +
                "காரணம்: மாக்னபோர்தி ஓரைசே பூஞ்சான் — காற்று மற்றும் மழை நீர் தெறிப்பால் பரவும்.\n" +
                "சிகிச்சை:\n" +
                "1. பாதிக்கப்பட்ட இலை, தண்டுகளை உடனடியாக கிள்ளி எடுத்து வயலுக்கு வெளியே எரிக்கவும்.\n" +
                "2. ட்ரைசைக்கிள்சோல் 75 WP 0.6 கிராம்/லிட்டர் தண்ணீரில் கலந்து 10 நாட்களுக்கு ஒருமுறை தெளிக்கவும்.\n" +
                "3. 10 நாட்களுக்கு பிறகு புதிய புள்ளிகள் இருந்தால் மீண்டும் தெளிக்கவும்.\n" +
                "தடுப்பு முறைகள்:\n" +
                "1. நோய் எதிர்ப்பு திறன் மிக்க ரகங்களைப் பயன்படுத்தவும்; அதிக தழை சத்து இடாதீர்கள்.\n" +
                "2. விதை நேர்த்தி: கார்பெண்டாசிம் 2 கிராம்/கிலோ விதை கலந்து விதைக்கவும்.\n" +
                "மருந்து: ட்ரைசைக்கிள்சோல் 75 WP – 0.6 கிராம்/லிட்டர் (உள்ளூர் வேளாண் கடையில் கிடைக்கும்)";
        return new CropResponse(diagnosis, false, "AI பரிந்துரை", "");
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromGeminiResponse(Map<String, Object> responseBody, String language) {
        if (responseBody == null) {
            log.warn("Gemini returned null response body");
            return "சேவை தற்காலிகமாக நிறுத்தப்பட்டுள்ளது.";
        }
        try {
            List<Map<String, Object>> candidates =
                    (List<Map<String, Object>>) responseBody.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                String finishReason = (String) candidates.get(0).get("finishReason");
                if ("SAFETY".equals(finishReason) || "RECITATION".equals(finishReason)) {
                    log.warn("Gemini Vision blocked: finishReason={}", finishReason);
                    return getDemoCropResponse(language).getDiagnosis();
                }
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    return (String) parts.get(0).get("text");
                }
            }
            log.warn("Gemini Vision returned no usable candidates");   // the body is not logged: it may hold model output
        } catch (Exception e) {
            log.warn("Error parsing Gemini Vision response: {}", LogSafe.error(e));
        }
        return "சேவை தற்காலிகமாக நிறுத்தப்பட்டுள்ளது.";
    }

    private Optional<CropAdvisory> findMatchingAdvisory(String geminiDiagnosis, String language) {
        if (geminiDiagnosis == null) return Optional.empty();
        try {
            String[] lines = geminiDiagnosis.split("\n");
            for (String line : lines) {
                if (line.contains("நோய்:") || line.contains("Disease:")) {
                    String diseasePart = line.replace("நோய்:", "").replace("Disease:", "").trim();
                    if (!diseasePart.isEmpty()) {
                        String[] diseaseWords = diseasePart.split(" ");
                        for (String word : diseaseWords) {
                            if (word.length() > 3) {
                                Optional<CropAdvisory> found =
                                        advisoryRepo.findByDiseaseNameContainingIgnoreCase(word);
                                if (found.isPresent()) {
                                    return found;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not find matching advisory: {}", LogSafe.error(e));
        }
        return Optional.empty();
    }
}
