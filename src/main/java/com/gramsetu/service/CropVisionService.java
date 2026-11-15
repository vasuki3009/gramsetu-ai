package com.gramsetu.service;

import com.gramsetu.util.CropDiagnosis;
import com.gramsetu.util.ImageResizer;
import com.gramsetu.util.LogSafe;
import com.gramsetu.dto.CropResponse;
import com.gramsetu.model.CropAdvisory;
import com.gramsetu.repository.CropAdvisoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Slf4j
public class CropVisionService {

    @Autowired
    private CropAdvisoryRepository advisoryRepo;

    @Autowired
    private SarvamSpeechService sarvamService;

    /** Model chain, retries, timeouts, thinking level and output cap are shared with the text answers. */
    @Autowired
    private AIAdvisorService aiAdvisor;

    public CropResponse analyzeCropImage(byte[] imageBytes, String language) {
        if (!aiAdvisor.isKeyConfigured()) {
            log.warn("Gemini API key not configured – crop analysis unavailable. " +
                     "Set gemini.api.key in application.properties");
            return unavailableResponse(language);
        }
        try {
            // Web uploads can be several MB; WhatsApp photos are already small. Both are capped at 1024 px.
            imageBytes = ImageResizer.limitDimension(imageBytes, ImageResizer.MAX_DIMENSION);
            String mimeType = detectMimeType(imageBytes);
            log.debug("Analyzing crop image: {} bytes, MIME: {}", imageBytes.length, mimeType);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            String prompt = "english".equals(language) ? """
                    You are an expert agricultural scientist helping farmers in Tamil Nadu.
                    Look carefully at this photo.

                    Step 1: Identify the crop you see. Do NOT assume a crop and do NOT default to
                    paddy/rice or any other crop. It can be any plant.
                    Step 2: Describe what is actually visible on the plant (colour, shape, size and
                    position of spots, patches, holes or discolouration) BEFORE naming any disease.
                    Step 3: State your confidence: high, medium or low.
                    Step 4: Diagnose a disease ONLY for the crop you identified, and only from the
                    symptoms you described.

                    If the photo is not a plant, is blurry, too dark, too far away, shows several
                    different plants, or you cannot tell which plant it is, do NOT guess. Reply with ONLY
                    these three lines and nothing else:
                    Crop: [crop name, or "unclear" / "not a crop leaf"]
                    Confidence: low
                    Result: I am not sure.
                    In that case never name any disease, treatment or medicine.

                    If a plant is clearly visible but you are not confident about the cause, reply with
                    ONLY these four lines and nothing else:
                    Crop: [your best guess of the crop]
                    Confidence: low
                    Symptoms: [what is visible, in one short sentence]
                    Disease: [one possible problem, name only]
                    In that case never write any treatment or medicine.

                    Otherwise (you are confident) reply ONLY in this exact format in English:

                    Crop: [crop name]
                    Confidence: [one English word: high or medium]
                    Symptoms: [2-3 visible symptoms observed in this image]
                    Disease: [full disease name with scientific name if known, for that crop]
                    Severity: [mild / moderate / severe - only if you can judge it from the photo, otherwise leave this line out]
                    Cause: [root cause - fungus / bacteria / virus / pest / nutrient deficiency]
                    Treatment:
                    1. [immediate action - remove/isolate]
                    2. [chemical or biological treatment with dosage]
                    3. [follow-up action after 7-10 days]
                    Prevention:
                    1. [preventive cultural practice]
                    2. [preventive spray or seed treatment]
                    Medicine: [specific pesticide / fungicide name only; follow the label dosage]

                    No extra text before or after.
                    """ : """
                    நீங்கள் தமிழ்நாடு வேளாண் விஞ்ஞானி. இந்த படத்தை கவனமாக ஆய்வு செய்யவும்.

                    படி 1: படத்தில் எந்தப் பயிர் தெரிகிறது என்று முதலில் அடையாளம் காணவும். எந்தப் பயிரையும்
                    (நெல் உட்பட) முன்கூட்டியே ஊகிக்க வேண்டாம். எந்தச் செடியாகவும் இருக்கலாம்.
                    படி 2: எந்த நோயின் பெயரையும் சொல்வதற்கு முன், செடியில் உண்மையில் தெரிவதை (புள்ளிகள், திட்டுகள், துளைகள்,
                    நிற மாற்றம் - அவற்றின் நிறம், வடிவம், இடம்) முதலில் விவரிக்கவும்.
                    படி 3: உங்கள் நம்பிக்கை அளவைச் சொல்லவும்: high, medium அல்லது low.
                    படி 4: நீங்கள் அடையாளம் கண்ட அந்தப் பயிருக்கு மட்டும், நீங்கள் விவரித்த அறிகுறிகளை வைத்து மட்டும் நோயைக் கண்டறியவும்.

                    வேறுபாடுகள்:
                    - புள்ளிகள், கருப்பு/பழுப்பு திட்டுகள், எரிந்த விளிம்புகள் = பூஞ்சை அல்லது பாக்டீரியா நோய்
                    - சீரற்ற மஞ்சள் நிறம் (வரிகள் இல்லாமல்) = வைரஸ் நோய்
                    - சீரான வெளிர் மஞ்சள் நிறம் (புள்ளிகள் இல்லாமல்) = சத்துக்குறைபாடு
                    - இலையில் துளைகள், கடித்த தடங்கள் = பூச்சி தாக்குதல்

                    படம் செடியே இல்லை, மங்கலாக / இருட்டாக / தொலைவில் உள்ளது, பல செடிகள் கலந்துள்ளன, அல்லது
                    எந்தச் செடி என்றே உங்களுக்குத் தெரியவில்லை என்றால் ஊகிக்க வேண்டாம். அப்போது இந்த மூன்று வரிகளை மட்டும் எழுதவும்:
                    பயிர்: [பயிரின் பெயர், அல்லது "தெளிவில்லை" / "பயிர் இலை இல்லை"]
                    நம்பிக்கை: low
                    முடிவு: எனக்கு உறுதியாகத் தெரியவில்லை.
                    அப்போது எந்த நோய், சிகிச்சை, மருந்தின் பெயரையும் எழுதக் கூடாது.

                    செடி தெளிவாகத் தெரிகிறது, ஆனால் காரணம் பற்றி உங்களுக்கு உறுதி இல்லை என்றால், இந்த நான்கு வரிகளை மட்டும் எழுதவும்:
                    பயிர்: [உங்கள் சிறந்த ஊகம்]
                    நம்பிக்கை: low
                    அறிகுறிகள்: [படத்தில் தெரிவது, ஒரு சிறு வாக்கியத்தில்]
                    நோய்: [இருக்கக்கூடிய ஒரு பிரச்சனை - பெயர் மட்டும்]
                    அப்போது எந்த சிகிச்சை, மருந்தையும் எழுதக் கூடாது.

                    இல்லையெனில் (உறுதியாக இருந்தால்), கீழ்க்கண்ட வடிவத்தில் தமிழில் மட்டுமே பதில் சொல்லவும்:

                    பயிர்: [பயிரின் பெயர்]
                    நம்பிக்கை: [ஒரு ஆங்கிலச் சொல்: high அல்லது medium]
                    அறிகுறிகள்: [இந்த படத்தில் தெரியும் 2-3 குறிப்பிட்ட அறிகுறிகள் - நிறம், வடிவம், இடம்]
                    நோய்: [அந்தப் பயிருக்கான சரியான நோயின் பெயர் தமிழிலும் ஆங்கிலத்திலும்]
                    தீவிரம்: [லேசானது / மிதமானது / கடுமையானது - படத்தில் இருந்து மதிப்பிட முடிந்தால் மட்டும்; இல்லையெனில் இந்த வரியை விட்டுவிடவும்]
                    காரணம்: [மூல காரணம் - பூஞ்சான் / பாக்டீரியா / வைரஸ் / பூச்சி / சத்துக்குறைபாடு]
                    சிகிச்சை:
                    1. [உடனடி நடவடிக்கை]
                    2. [சரியான மருந்து வகை - பூஞ்சானுக்கு fungicide, பாக்டீரியாவுக்கு bactericide, சத்துக்குறைபாட்டிற்கு உரம்]
                    3. [7-10 நாட்களில் மேற்கொள்ள வேண்டியது]
                    தடுப்பு முறைகள்:
                    1. [நடைமுறை தடுப்பு]
                    2. [தடுப்பு மருந்து அல்லது இயற்கை முறை]
                    மருந்து: [மருந்தின் பெயர் மட்டும். அளவு எழுத வேண்டாம்; மருந்து டப்பாவில் உள்ள அளவைப் பின்பற்றவும்]

                    வேறு எந்த உரையும் வேண்டாம்.
                    """;

            // Retries, fallback models and the overall deadline happen inside; empty means every model failed.
            String geminiDiagnosis = aiAdvisor.generateFromImage(prompt, mimeType, base64Image).orElse(null);
            if (geminiDiagnosis == null || geminiDiagnosis.isBlank()) {
                log.warn("Crop vision: no model produced a diagnosis - returning the could-not-analyse message");
                return unavailableResponse(language);
            }
            log.debug("Gemini crop diagnosis received ({} chars)", geminiDiagnosis.length());

            // Show a diagnosis only when the model named the crop with medium/high confidence.
            // Low confidence with a named plant, visible symptoms and a possible problem gets a clearly
            // "not sure" tentative reply (no medicine, no verified line); everything else gets the fixed message.
            CropDiagnosis.UnsureReason reason = CropDiagnosis.unsureReason(geminiDiagnosis);
            if (reason != null) {
                String tentative = reason == CropDiagnosis.UnsureReason.LOW_CONFIDENCE
                        ? CropDiagnosis.tentativeMessage(geminiDiagnosis, language) : null;
                // Reason code and the label names found (text before a colon, max 10 x 20 chars) only -
                // never the answer text or any value.
                log.info("Crop vision: unsure reason={} tentative={} labels={}", reason, tentative != null,
                        CropDiagnosis.labelsFound(geminiDiagnosis));
                return new CropResponse(tentative != null ? tentative : CropDiagnosis.unsureMessage(language),
                        false, "", "", true);
            }

            // Verified only when BOTH the crop and the disease match one advisory entry.
            Optional<CropAdvisory> advisory = findMatchingAdvisory(geminiDiagnosis);
            if (advisory.isPresent()) {
                geminiDiagnosis = CropDiagnosis.withVerifiedSolution(
                        geminiDiagnosis, advisory.get().getVerifiedSolution(), language);
            }
            geminiDiagnosis = CropDiagnosis.withKvkNote(geminiDiagnosis, language);

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
            return unavailableResponse(language);
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

    /** Honest "could not analyse" answer - never a made-up diagnosis. */
    private CropResponse unavailableResponse(String language) {
        return new CropResponse(CropDiagnosis.unavailableMessage(language), false, "", "", true);
    }

    /** Tamil / alternative names for the crops in the advisory table (lower-case token -> table crop name). */
    private static final Map<String, String> CROP_ALIASES = Map.ofEntries(
            Map.entry("தக்காளி", "tomato"),
            Map.entry("மாம்பழம்", "mango"), Map.entry("மாமரம்", "mango"), Map.entry("மா", "mango"),
            Map.entry("நிலக்கடலை", "groundnut"), Map.entry("வேர்க்கடலை", "groundnut"),
            Map.entry("peanut", "groundnut"),
            Map.entry("மஞ்சள்", "turmeric"),
            Map.entry("கத்தரிக்காய்", "brinjal"), Map.entry("கத்திரிக்காய்", "brinjal"),
            Map.entry("eggplant", "brinjal"), Map.entry("aubergine", "brinjal"));

    private static final Pattern NON_LETTER = Pattern.compile("[^\\p{L}\\p{M}]+");

    /**
     * Strict match: an advisory counts only if the crop the model named equals the advisory's crop
     * (whole word, English or Tamil alias) AND the model's disease line contains the advisory's disease
     * name. A crop-only or disease-only match is not verified.
     */
    Optional<CropAdvisory> findMatchingAdvisory(String diagnosis) {
        if (diagnosis == null) return Optional.empty();
        try {
            String crop = CropDiagnosis.crop(diagnosis);
            String disease = CropDiagnosis.disease(diagnosis);
            if (crop == null || crop.isBlank() || disease == null || disease.isBlank()) return Optional.empty();

            String diseaseNorm = normalize(disease);
            Set<String> cropNames = new LinkedHashSet<>();
            for (String token : NON_LETTER.split(crop.toLowerCase(Locale.ROOT))) {
                if (token.isBlank()) continue;
                cropNames.add(token);
                String alias = CROP_ALIASES.get(token);
                if (alias != null) cropNames.add(alias);
            }
            for (String cropName : cropNames) {
                for (CropAdvisory a : advisoryRepo.findByCropTypeIgnoreCase(cropName)) {
                    if (diseaseMatches(a, diseaseNorm)) return Optional.of(a);
                }
            }
        } catch (Exception e) {
            log.warn("Could not find matching advisory: {}", LogSafe.error(e));
        }
        return Optional.empty();
    }

    /** Full advisory disease name, or that name without its leading crop word, appears in the model's disease. */
    private static boolean diseaseMatches(CropAdvisory a, String diseaseNorm) {
        if (a.getDiseaseName() == null || a.getDiseaseName().isBlank()) return false;
        String name = normalize(a.getDiseaseName());
        if (diseaseNorm.contains(name)) return true;
        String crop = a.getCropType() == null ? "" : normalize(a.getCropType());
        if (!crop.isEmpty() && name.startsWith(crop + " ")) {
            return diseaseNorm.contains(name.substring(crop.length() + 1));
        }
        return false;
    }

    private static String normalize(String s) {
        return NON_LETTER.matcher(s.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }
}
