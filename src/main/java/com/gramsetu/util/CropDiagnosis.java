package com.gramsetu.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the structured answer of the crop-photo model and decides whether it can be shown as a
 * diagnosis. A diagnosis is shown only when the model named the crop, stated medium/high confidence and
 * named a disease. Everything else becomes one of the fixed messages below, which never name a
 * disease or a medicine.
 */
public final class CropDiagnosis {

    private CropDiagnosis() {}

    public static final String KVK_TAMIL =
            "உறுதிப்படுத்த உங்கள் அருகிலுள்ள வேளாண் அலுவலரை அல்லது வேளாண் அறிவியல் நிலையத்தை (KVK) அணுகவும்.";
    public static final String KVK_ENGLISH =
            "Please confirm with your local agriculture officer or Krishi Vigyan Kendra (KVK) before treating.";

    public static final String UNSURE_TAMIL =
            "எனக்கு உறுதியாகத் தெரியவில்லை. படம் தெளிவாக இல்லாமல் இருக்கலாம் அல்லது இது பயிர் இலையாக இல்லாமல் இருக்கலாம். "
            + "இலையை நல்ல வெளிச்சத்தில், அருகில் இருந்து மீண்டும் படம் எடுத்து அனுப்பவும்.\n\nகுறிப்பு: " + KVK_TAMIL;
    public static final String UNSURE_ENGLISH =
            "I am not sure. The photo may be unclear, or it may not be a crop leaf. "
            + "Please send a close, well-lit photo of the affected leaf again.\n\nNote: " + KVK_ENGLISH;

    public static final String UNAVAILABLE_TAMIL =
            "இப்போது படத்தை ஆய்வு செய்ய முடியவில்லை. சிறிது நேரம் கழித்து மீண்டும் முயற்சிக்கவும்.\n\nகுறிப்பு: " + KVK_TAMIL;
    public static final String UNAVAILABLE_ENGLISH =
            "The photo could not be analysed right now. Please try again in a little while.\n\nNote: " + KVK_ENGLISH;

    private static final String[] CROP = {"Crop", "பயிர்"};
    private static final String[] CONFIDENCE = {"Confidence", "நம்பிக்கை"};
    private static final String[] DISEASE = {"Disease", "நோய்"};

    private static final String[] UNCLEAR_CROP_WORDS = {
            "unclear", "not a crop", "not sure", "unknown", "unidentified", "cannot", "can't",
            "தெளிவில்லை", "தெரியவில்லை", "பயிர் இல்லை"};
    private static final String[] NOT_SURE_PHRASES = {
            "i am not sure", "i'm not sure", "உறுதியாகத் தெரியவில்லை"};

    public static String unsureMessage(String language) {
        return "english".equals(language) ? UNSURE_ENGLISH : UNSURE_TAMIL;
    }

    public static String unavailableMessage(String language) {
        return "english".equals(language) ? UNAVAILABLE_ENGLISH : UNAVAILABLE_TAMIL;
    }

    /** "high", "medium", "low", or null when the model gave no readable confidence. */
    public static String confidence(String text) {
        String value = lineValue(text, CONFIDENCE);
        if (value == null) return null;
        String v = value.toLowerCase(Locale.ROOT);
        if (v.contains("low")) return "low";
        if (v.contains("medium")) return "medium";
        if (v.contains("high")) return "high";
        return null;
    }

    /** The crop the model says it sees, or null. */
    public static String crop(String text) {
        return lineValue(text, CROP);
    }

    /** The disease line of the answer, or null. */
    public static String disease(String text) {
        return lineValue(text, DISEASE);
    }

    /**
     * True unless the answer names a crop, has medium/high confidence and names a disease.
     * Missing or unreadable confidence counts as unsure.
     */
    public static boolean isUnsure(String text) {
        return unsureReason(text) != null;
    }

    /** Why an answer cannot be shown as a diagnosis. Only these codes are ever logged, never the answer. */
    public enum UnsureReason {
        EMPTY_ANSWER, NO_CONFIDENCE_LINE, LOW_CONFIDENCE, NO_CROP, UNCLEAR_CROP, NOT_SURE_PHRASE, NO_DISEASE_SECTION
    }

    /** The first reason the answer is unsure, or null when it can be shown as a diagnosis. */
    public static UnsureReason unsureReason(String text) {
        if (text == null || text.isBlank()) return UnsureReason.EMPTY_ANSWER;
        String conf = confidence(text);
        if (conf == null) return UnsureReason.NO_CONFIDENCE_LINE;
        if ("low".equals(conf)) return UnsureReason.LOW_CONFIDENCE;
        String crop = crop(text);
        if (crop == null || crop.isBlank()) return UnsureReason.NO_CROP;
        if (containsAny(crop, UNCLEAR_CROP_WORDS)) return UnsureReason.UNCLEAR_CROP;
        if (containsAny(text, NOT_SURE_PHRASES)) return UnsureReason.NOT_SURE_PHRASE;
        String disease = lineValue(text, DISEASE);
        if (disease == null || disease.isBlank()) return UnsureReason.NO_DISEASE_SECTION;
        return null;
    }

    private static boolean containsAny(String text, String[] needles) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String n : needles) {
            if (lower.contains(n)) return true;
        }
        return false;
    }

    // ---- Tentative reply: low confidence, but a plant, visible symptoms and a possible problem were named ----

    private static final String[] SYMPTOMS = {"Symptoms", "அறிகுறிகள்"};

    /** Words that mean the model slipped treatment advice into a field that should only describe or name. */
    private static final String[] TREATMENT_WORDS = {
            "medicine", "spray", "fungicide", "pesticide", "insecticide", "bactericide", "dosage", "apply", "treat",
            "மருந்து", "தெளி", "சிகிச்சை"};
    private static final Pattern DOSE = Pattern.compile("\\d\\s*(ml|gm|g|kg|l)\\b", Pattern.CASE_INSENSITIVE);

    public static final String TENTATIVE_TAMIL_FORMAT =
            "எனக்கு முழுமையாக உறுதியாகத் தெரியவில்லை. கீழே உள்ளது என் ஊகம் மட்டுமே, உறுதியான முடிவு அல்ல.\n\n"
            + "பயிர் (ஊகம்): %s\n"
            + "படத்தில் தெரிவது: %s\n"
            + "இருக்கக்கூடிய பிரச்சனை (உறுதியில்லை): %s\n\n"
            + "உறுதியாகத் தெரியும் வரை எந்த மருந்தும் தெளிக்க வேண்டாம். "
            + "தயவுசெய்து இலையை நல்ல வெளிச்சத்தில், அருகில் இருந்து மீண்டும் படம் எடுத்து அனுப்பவும்.\n\n"
            + "குறிப்பு: " + KVK_TAMIL;
    public static final String TENTATIVE_ENGLISH_FORMAT =
            "I am not fully sure. What follows is only my best guess, not a confirmed result.\n\n"
            + "Crop (guess): %s\n"
            + "What is visible: %s\n"
            + "Possible problem (not confirmed): %s\n\n"
            + "Please do not spray any medicine until this is confirmed. "
            + "Kindly send a close, well-lit photo of the affected leaf again.\n\n"
            + "Note: " + KVK_ENGLISH;

    /**
     * For a LOW-confidence answer that still named a plant, visible symptoms and a possible problem: a fixed,
     * clearly "not sure" reply built only from those three short values. It never contains a medicine, a
     * treatment or a verified line. Null when the answer is not suitable (use the full not-sure reply then).
     */
    public static String tentativeMessage(String text, String language) {
        if (!"low".equals(confidence(text))) return null;
        if (containsAny(text, NOT_SURE_PHRASES)) return null;
        String crop = crop(text);
        String symptoms = lineValue(text, SYMPTOMS);
        String disease = disease(text);
        if (!usable(crop, 60) || !usable(symptoms, 220) || !usable(disease, 100)) return null;
        for (String v : new String[]{crop, symptoms, disease}) {
            if (containsAny(v, UNCLEAR_CROP_WORDS) || containsAny(v, TREATMENT_WORDS) || DOSE.matcher(v).find()) {
                return null;
            }
        }
        String format = "english".equals(language) ? TENTATIVE_ENGLISH_FORMAT : TENTATIVE_TAMIL_FORMAT;
        return String.format(format, crop, symptoms, disease);
    }

    private static boolean usable(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    /** Adds the "confirm with KVK" note to a confident answer (once). */
    public static String withKvkNote(String text, String language) {
        if (text == null) return null;
        boolean english = "english".equals(language);
        return text.stripTrailing() + "\n\n" + (english ? "Note: " + KVK_ENGLISH : "குறிப்பு: " + KVK_TAMIL);
    }

    public static final String VERIFIED_LABEL_TAMIL = "TNAU பரிந்துரை:";
    public static final String VERIFIED_LABEL_ENGLISH = "TNAU recommendation:";

    private static final Pattern MEDICINE_LINE =
            Pattern.compile("(?im)^[ \\t\\-•*]*(?:Medicine|மருந்து)[\\s*]*:.*(?:\\R|$)");

    /**
     * For a verified advisory match: drops the model's own medicine line and adds the advisory's
     * solution as a separate labelled section, so only one medicine recommendation is shown.
     */
    public static String withVerifiedSolution(String text, String solution, String language) {
        if (text == null) return null;
        String withoutMedicine = MEDICINE_LINE.matcher(text).replaceAll("").stripTrailing();
        if (solution == null || solution.isBlank()) return withoutMedicine;
        String label = "english".equals(language) ? VERIFIED_LABEL_ENGLISH : VERIFIED_LABEL_TAMIL;
        return withoutMedicine + "\n" + label + " " + solution.strip();
    }

    private static String lineValue(String text, String[] labels) {
        if (text == null) return null;
        String clean = text.replace("*", "");
        for (String label : labels) {
            Matcher m = Pattern.compile("(?im)^[\\s\\-•]*" + Pattern.quote(label) + "\\s*:[ \\t]*(.*)$").matcher(clean);
            if (m.find()) return m.group(1).strip();
        }
        return null;
    }
}
