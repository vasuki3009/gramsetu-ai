package com.gramsetu.util;

/**
 * Builds Gemini prompts tailored to the detected Tamil dialect.
 *
 * Each dialect gets a distinct persona and tone instruction so the AI
 * response feels natural to a speaker of that regional variety.
 */
public final class DialectPromptBuilder {

    private DialectPromptBuilder() {}

    /**
     * Builds a dialect-aware Gemini prompt for a Tamil farming question.
     *
     * @param userMessage  Transcribed question from the farmer (Tamil text)
     * @param dialect      Detected dialect code: KONGU, MADURAI, NELLAI, MADRAS, CHENNAI, or STANDARD
     * @param context      Optional extra context (crop name, location, season); may be null
     * @return             Complete prompt string ready to pass to Gemini
     */
    public static String buildPrompt(String userMessage, String dialect, String context) {
        String dialectInstruction = buildDialectInstruction(dialect);

        StringBuilder prompt = new StringBuilder();
        prompt.append(dialectInstruction);
        prompt.append("\n\n");
        prompt.append("விவசாயியின் கேள்வி: ").append(userMessage);

        if (context != null && !context.isBlank()) {
            prompt.append("\n\nகூடுதல் தகவல்: ").append(context);
        }

        // Length, structure and safety rules come from the system prompt (AdvisorPrompts).
        prompt.append("\n\nAnswer in the same dialect style.");

        return prompt.toString();
    }

    private static String buildDialectInstruction(String dialect) {
        if (dialect == null) {
            return buildStandardInstruction();
        }
        return switch (dialect.toUpperCase()) {
            case "KONGU" -> buildKonguInstruction();
            case "MADURAI" -> buildMaduraiInstruction();
            case "NELLAI" -> buildNellaiInstruction();
            case "MADRAS", "CHENNAI" -> buildMadrasInstruction();
            default -> buildStandardInstruction();
        };
    }

    // ── Dialect-specific prompt templates ────────────────────────────────────

    private static String buildKonguInstruction() {
        return "நீங்கள் கோயம்புத்தூர் / ஹோசூர் பகுதி விவசாயிகளுக்கான வேளாண் நிபுணர்.\n" +
               "கொங்கு தமிழ் பேசும் விவசாயி கேட்கிறார். அவருக்கு அவர் மொழியிலேயே பதில் சொல்லவும்.\n" +
               "தொனி: நட்பான, கிராம மூத்தவர் போல. 'ஆங்க', 'வாங்க', 'போங்க' போன்ற சொற்களை இயற்கையாக பயன்படுத்தவும்.\n" +
               "உதாரணம் தொடக்கம்: 'ஆங்க, இந்த பிரச்சனைக்கு...' அல்லது 'வாங்க பாப்போம்...'.\n" +
               "நேரடியாக தீர்வு சொல்லவும் — அறிமுகம் தேவையில்லை.";
    }

    private static String buildMaduraiInstruction() {
        return "நீங்கள் மதுரை / திண்டுக்கல் பகுதி விவசாயிகளுக்கான வேளாண் நிபுணர்.\n" +
               "மதுரை வீச்சு தமிழ் பேசும் விவசாயி கேட்கிறார். அவர் மொழியில் தைரியமாக, நேரடியாக பதில் சொல்லவும்.\n" +
               "தொனி: உறுதியான, வலிமையான மதுரை வீச்சு. 'என்னடா', 'பாருடா', 'சொல்லுடா' போன்ற சொற்கள் இயற்கையாக வரட்டும்.\n" +
               "உதாரணம் தொடக்கம்: 'அதுக்கு இப்படி பண்ணுடா...' அல்லது 'கேளுடா, இந்த மருந்து போடு...'.\n" +
               "நேரடியாக தீர்வு சொல்லவும் — அறிமுகம் தேவையில்லை.";
    }

    private static String buildNellaiInstruction() {
        return "நீங்கள் திருநெல்வேலி / தூத்துக்குடி பகுதி விவசாயிகளுக்கான வேளாண் நிபுணர்.\n" +
               "நெல்லை தமிழ் பேசும் விவசாயி கேட்கிறார். அவர் இசை போன்ற நெல்லை தமிழிலேயே பதில் சொல்லவும்.\n" +
               "தொனி: இனிமையான, நெல்லை பாணி. 'என்னாம்', 'வாரம்', 'ஆமாம்', 'சொல்லுவாம்' போன்ற சொற்கள் பயன்படுத்தவும்.\n" +
               "உதாரணம் தொடக்கம்: 'ஆமாம், இந்த பிரச்சனைக்கு...' அல்லது 'சொல்லுவாம், இப்படி பண்ணணும்...'.\n" +
               "நேரடியாக தீர்வு சொல்லவும் — அறிமுகம் தேவையில்லை.";
    }

    private static String buildMadrasInstruction() {
        return "நீங்கள் சென்னை / காஞ்சிபுரம் பகுதி விவசாயிகளுக்கான வேளாண் நிபுணர்.\n" +
               "மெட்ராஸ் / சென்னை பேச்சு தமிழ் பேசும் விவசாயி கேட்கிறார். அவர் மொழியில் நகரத்தனமான, கானா வேர் கலந்த தமிழில் பதில் சொல்லவும்.\n" +
               "தொனி: ஆற்றல்மிக்க, நேரடியான சென்னை பேச்சு. 'மச்சான்', 'நைனா', 'என்னமா' போன்ற சொற்கள் இயற்கையாக வரட்டும்.\n" +
               "உதாரணம் தொடக்கம்: 'மச்சான், இதுக்கு...' அல்லது 'நைனா கேளு, இப்படி பண்ணு...'.\n" +
               "நேரடியாக தீர்வு சொல்லவும் — அறிமுகம் தேவையில்லை.";
    }

    private static String buildStandardInstruction() {
        return "நீங்கள் தமிழ்நாடு விவசாயிகளுக்கான வேளாண் நிபுணர்.\n" +
               "தெளிவான, எளிய நடையில் தமிழில் பதில் சொல்லவும்.\n" +
               "தொனி: அன்பான, அணுகக்கூடிய விவசாய ஆலோசகர்.\n" +
               "நேரடியாக தீர்வு சொல்லவும் — அறிமுகம் தேவையில்லை.";
    }
}
