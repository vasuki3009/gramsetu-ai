package com.gramsetu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DialectTranslationService {

    /**
     * Transforms standard Tamil text into dialect-specific colloquial Tamil.
     * Applies phonetic and grammatical patterns specific to each Tamil dialect.
     */
    public String transformToDialect(String standardTamil, String dialect) {
        if (standardTamil == null || standardTamil.isBlank()) {
            return standardTamil;
        }

        if (dialect == null || dialect.isBlank() || dialect.equalsIgnoreCase("standard") || dialect.equalsIgnoreCase("tamil")) {
            return standardTamil;
        }

        log.info("[Dialect] Transforming text to {} dialect", dialect);

        String transformed = standardTamil;

        switch (dialect.toLowerCase()) {
            case "kongu":
            case "krishnagiri":
            case "hosur":
            case "coimbatore":
                transformed = applyKonguTransformations(transformed);
                log.info("[Dialect] Applied Kongu transformations");
                break;

            case "madurai":
            case "southern":
            case "south":
                transformed = applyMaduraiTransformations(transformed);
                log.info("[Dialect] Applied Madurai transformations");
                break;

            case "nellai":
            case "tirunelveli":
            case "thoothukudi":
                transformed = applyNellaiTransformations(transformed);
                log.info("[Dialect] Applied Nellai transformations");
                break;

            case "chennai":
            case "madras":
                transformed = applyChennaiTransformations(transformed);
                log.info("[Dialect] Applied Chennai transformations");
                break;

            default:
                log.info("[Dialect] No transformation for dialect: {}", dialect);
        }

        return transformed;
    }

    /**
     * Kongu dialect (Coimbatore, Erode, Krishnagiri, Hosur region)
     * Characteristics:
     * - கிறீர்கள் → றீங்க
     * - வேண்டும் → வேணும்
     * - இருக்கிறது → இருக்கு
     */
    private String applyKonguTransformations(String text) {
        return text
            // Verb endings
            .replace("கிறீர்கள்", "றீங்க")
            .replace("கிறார்கள்", "றாங்க")
            .replace("கின்றனர்", "றாங்க")
            .replace("கிறான்", "றான்")
            .replace("கிறாள்", "றா")
            .replace("கிறது", "குது")
            .replace("கிறேன்", "றேன்")

            // Common words
            .replace("நீங்கள்", "நீங்க")
            .replace("அவர்கள்", "அவங்க")
            .replace("இவர்கள்", "இவங்க")
            .replace("வேண்டும்", "வேணும்")
            .replace("வேண்டாம்", "வேணாம்")
            .replace("கூடாது", "கூடாது")
            .replace("போகிறேன்", "போறேன்")
            .replace("வருகிறேன்", "வர்றேன்")

            // State verbs
            .replace("இருக்கிறது", "இருக்கு")
            .replace("இருக்கிறார்", "இருக்காரு")
            .replace("இருக்கிறேன்", "இருக்கேன்")

            // Questions
            .replace("என்ன செய்கிறீர்கள்", "என்ना செய்றீங்க")
            .replace("எப்படி இருக்கிறீர்கள்", "எப்படி இருக்கீங்க")
            .replace("எங்கே போகிறீர்கள்", "எங்க போறீங்க");
    }

    /**
     * Madurai dialect (Madurai, Dindigul, Theni region)
     * Characteristics:
     * - கிறீர்கள் → யுறீங்க
     * - More elongated pronunciations
     * - Distinct 'ய' insertion patterns
     */
    private String applyMaduraiTransformations(String text) {
        return text
            // Verb endings (characteristic 'ய' insertion)
            .replace("கிறீர்கள்", "யுறீங்க")
            .replace("கிறார்கள்", "யுறாங்க")
            .replace("கின்றனர்", "யுறாங்க")
            .replace("கிறான்", "யுறான்")
            .replace("கிறாள்", "யுறா")
            .replace("கிறது", "யுது")
            .replace("கிறேன்", "யுறேன்")

            // Common words
            .replace("நீங்கள்", "நீங்க")
            .replace("அவர்கள்", "அவங்க")
            .replace("இவர்கள்", "இவங்க")
            .replace("வேண்டும்", "வேணும்")
            .replace("வேண்டாம்", "வேணாம்")
            .replace("போகிறேன்", "போயுறேன்")
            .replace("வருகிறேன்", "வர்யுறேன்")

            // State verbs
            .replace("இருக்கிறது", "இருக்கு")
            .replace("இருக்கிறார்", "இருக்காரு")
            .replace("இருக்கிறேன்", "இருக்கேன்")

            // Questions
            .replace("என்ன செய்கிறீர்கள்", "என்ன செய்யுறீங்க")
            .replace("எப்படி இருக்கிறீர்கள்", "எப்படி இருக்கீங்க")
            .replace("எங்கே போகிறீர்கள்", "எங்க போயுறீங்க");
    }

    /**
     * Nellai/Tirunelveli dialect (Tirunelveli, Thoothukudi region)
     * Characteristics:
     * - Softer endings
     * - Less aggressive transformations
     * - Retains more formal structure
     */
    private String applyNellaiTransformations(String text) {
        return text
            // Verb endings (softer)
            .replace("கிறீர்கள்", "கிறீங்க")
            .replace("கிறார்கள்", "கிறாங்க")
            .replace("கின்றனர்", "கிறாங்க")
            .replace("கிறான்", "கிறான்")
            .replace("கிறாள்", "கிறா")

            // Common words
            .replace("நீங்கள்", "நீங்க")
            .replace("அவர்கள்", "அவங்க")
            .replace("வேண்டும்", "வேணும்")
            .replace("வேண்டாம்", "வேணாம்")

            // State verbs
            .replace("இருக்கிறது", "இருக்கு")
            .replace("இருக்கிறார்", "இருக்காரு")

            // Questions
            .replace("என்ன செய்கிறீர்கள்", "என்ன செய்கிறீங்க")
            .replace("எப்படி இருக்கிறீர்கள்", "எப்படி இருக்கீங்க");
    }

    /**
     * Chennai dialect (Chennai, Kancheepuram region)
     * Characteristics:
     * - Colloquial shortening: போகிறேன் → போயிட்டேன்
     * - Casual particles: டா, மச்சி, நைனா
     */
    private String applyChennaiTransformations(String text) {
        return text
            .replace("நீங்கள்", "நீங்க")
            .replace("அவர்கள்", "அவங்க")
            .replace("இவர்கள்", "இவங்க")
            .replace("வேண்டும்", "வேணும்")
            .replace("வேண்டாம்", "வேணாம்")
            .replace("போகிறேன்", "போயிட்டே இருக்கேன்")
            .replace("வருகிறேன்", "வந்திட்டே இருக்கேன்")
            .replace("இருக்கிறது", "இருக்கு")
            .replace("இருக்கிறார்", "இருக்காரு")
            .replace("கிறீர்கள்", "றீங்க")
            .replace("கிறார்கள்", "றாங்க")
            .replace("செய்யுங்கள்", "பண்ணுங்கடா")
            .replace("பாருங்கள்", "பாருங்கடா")
            .replace("கொடுங்கள்", "குடுங்கடா")
            .replace("சரிபாருங்க", "சரிபாருங்கடா")
            .replace("முயற்சிக்கவும்", "ட்ரை பண்ணுங்கடா")
            .replace("தொடர்பு கொள்ளுங்க", "கான்டாக்ட் பண்ணுங்கடா");
    }
}
