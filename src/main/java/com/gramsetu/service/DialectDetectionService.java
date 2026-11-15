package com.gramsetu.service;

import com.gramsetu.dto.DialectResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyword-based dialect detection. There is no machine learning: each dialect has a list of marker words, and a
 * message scores the weight of every marker it contains.
 *
 * <ul>
 *   <li>STRONG markers (weight 2) are colloquial words that point to one region (verb endings, pronouns, slang).</li>
 *   <li>WEAK markers (weight 1) are common everywhere or only hint at a place (a district name, "இருக்கு").</li>
 *   <li>A dialect is chosen only when the top score is at least {@value #MIN_SCORE} and no other dialect ties it.
 *       Otherwise the configured default is used (DEFAULT_DIALECT, "standard" unless changed). One weak match
 *       therefore never wins, and never beats a strong match for another dialect.</li>
 * </ul>
 *
 * The result's {@code detectedBy} is "detected", "override" (the farmer chose a dialect) or "default".
 * Only the dialect and that source are logged, never the message text.
 */
@Service
@Slf4j
public class DialectDetectionService {

    static final int STRONG = 2;
    static final int WEAK = 1;
    static final int MIN_SCORE = 2;

    public static final String SOURCE_DETECTED = "detected";
    public static final String SOURCE_OVERRIDE = "override";
    public static final String SOURCE_DEFAULT = "default";

    /** Dialect used when nothing is detected: standard, kongu, madurai, nellai or chennai (DEFAULT_DIALECT). */
    @Value("${dialect.default:standard}")
    private String defaultDialect = "standard";

    record Marker(String word, int weight) {}

    /** Marker lists by dialect code. Package-private so tests can count them. */
    static final Map<String, List<Marker>> MARKERS = new LinkedHashMap<>();

    static {
        // ── KONGU (Coimbatore / Erode / Salem / Hosur) ──────────────────────────────
        MARKERS.put("KONGU", markers(
            // strong
            new String[] {
                "போகுது", "வருகுது", "ஆகுது",              // -குது verb endings
                "ஏனுங்க", "ஆமுங்க", "என்னுங்க",             // "-உங்க" politeness endings
                "ஊட்டு"                                     // வீடு (house)
            },
            // weak
            new String[] {
                "செத்த",                                    // a little; weak because it is inside "செத்தது" (died)
                "இருக்கு", "வேணும", "வேணும்", "பாருங்க", "கேளுங்க", "சொல்லுங்க", "பண்ணுங்க",
                "வாங்க", "நீங்க", "அவங்க", "இவங்க", "எங்க", "தெரியுமா",
                "மண்ணு", "தண்ணி", "நெல்லு",
                "கோவை", "ஈரோடு", "திருப்பூர்", "சேலம்", "கொங்கு",
                "கிருஷ்ணகிரி", "ஓசூர்", "தர்மபுரி", "நாமக்கல்"
            }));

        // ── MADURAI (Madurai / Dindigul / Theni / Virudhunagar) ──────────────────────
        MARKERS.put("MADURAI", markers(
            new String[] {
                "போயுறேன்", "என்னடா", "போடா", "பாருடா", "சொல்லுடா",
                "யுறான்", "யுறா", "யுது", "யுறேன்", "யுறீங்க",
                "அம்புட்டு", "இம்புட்டு",                   // அவ்வளவு / இவ்வளவு (that much / this much)
                "கம்மாய்"                                   // village tank / pond
            },
            new String[] {
                "வெள்ளாமை",                                 // farming (southern word)
                "மதுரை", "தேனி", "திண்டுக்கல்", "விருதுநகர்", "ராமநாதபுரம்"
            }));

        // ── NELLAI (Tirunelveli / Thoothukudi / Kanniyakumari) ───────────────────────
        MARKERS.put("NELLAI", markers(
            new String[] {
                "என்னங்க", "சொல்லுங்களேன்", "வாங்களேன்", "போங்களேன்", "கிறீங்க", "கிறாங்க", "இருக்கீங்களேன்",
                "ஏலே", "வாலே", "போலே",                      // "-லே" address
                "அண்ணாச்சி",                                // elder brother / shopkeeper
                "பண்ணுதேன்", "செய்யுதேன்", "பாக்குதேன்"       // -துதேன் verb endings
            },
            new String[] {
                "திருநெல்வேலி", "தூத்துக்குடி", "நெல்லை", "கன்னியாகுமரி"
            }));

        // ── CHENNAI ──────────────────────────────────────────────────────────────────
        MARKERS.put("CHENNAI", markers(
            new String[] {
                "நைனா", "மச்சி", "டேய்", "என்னப்பா", "மாப்பு",
                "போங்கப்பா", "வாப்பா", "சூப்பர்டா", "இல்லடா", "சரிடா", "ஓகேடா", "பாஸ்", "அண்ணே",
                "போயிட்டே", "வந்திட்டே", "பாத்திட்டே", "பண்ணிட்டே",
                "கீது", "கீறேன்", "கீறாங்க",                // இருக்கிறது / இருக்கிறேன் / இருக்கிறாங்க
                "இன்னா", "இன்னாப்பா",                       // என்ன
                "சோக்கா", "பேஜார்", "கம்முனு",
                "சென்னை", "கோடம்பாக்கம்"
            },
            new String[] {
                "மச்சான்", "ஜாஸ்தி", "அப்டி", "இப்டி", "பண்றது"
            }));
    }

    private static List<Marker> markers(String[] strong, String[] weak) {
        List<Marker> list = new ArrayList<>();
        for (String w : strong) list.add(new Marker(w, STRONG));
        for (String w : weak) list.add(new Marker(w, WEAK));
        return list;
    }

    private static final Map<String, String> REGIONS = Map.of(
        "KONGU", "Kongu Nadu", "MADURAI", "Madurai", "NELLAI", "Tirunelveli",
        "CHENNAI", "Chennai", "STANDARD", "Tamil Nadu");

    // ── Detection ──────────────────────────────────────────────────────────────

    public DialectResult detect(String text, String language, String location) {
        if ("english".equals(language) || text == null || text.isBlank()) {
            return finish(result("STANDARD", 100, SOURCE_DEFAULT));
        }

        Map<String, Integer> scores = score(text);
        int top = 0;
        for (int s : scores.values()) top = Math.max(top, s);

        List<String> leaders = new ArrayList<>();
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            if (e.getValue() == top) leaders.add(e.getKey());
        }

        if (top >= MIN_SCORE && leaders.size() == 1) {
            return finish(result(leaders.get(0), Math.min(top * 25, 95), SOURCE_DETECTED));
        }
        return finish(defaultResult());
    }

    /** Sum of marker weights found in the text, per dialect. */
    static Map<String, Integer> score(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (Map.Entry<String, List<Marker>> e : MARKERS.entrySet()) {
            List<Marker> found = new ArrayList<>();
            for (Marker m : e.getValue()) if (lower.contains(m.word())) found.add(m);
            int s = 0;
            for (Marker m : found) {
                // A marker inside a longer matched marker of the same dialect (இன்னா inside இன்னாப்பா) is the same
                // word, so it is counted once, for the longest match.
                boolean inLonger = false;
                for (Marker other : found) {
                    if (other != m && other.word().length() > m.word().length() && other.word().contains(m.word())) {
                        inLonger = true;
                        break;
                    }
                }
                if (!inLonger) s += m.weight();
            }
            scores.put(e.getKey(), s);
        }
        return scores;
    }

    private DialectResult defaultResult() {
        return result(normaliseDefault(defaultDialect), 70, SOURCE_DEFAULT);
    }

    /** Unknown or blank DEFAULT_DIALECT values mean standard Tamil. */
    static String normaliseDefault(String configured) {
        if (configured == null) return "STANDARD";
        String code = configured.trim().toUpperCase(Locale.ROOT);
        return MARKERS.containsKey(code) ? code : "STANDARD";
    }

    private static DialectResult result(String code, double confidence, String source) {
        return new DialectResult(code, confidence, REGIONS.get(code), source);
    }

    /** The one log line per message: dialect and source, never the text. */
    private static DialectResult finish(DialectResult r) {
        log.info("[Dialect] dialect={} source={}", r.getDialect(), r.getDetectedBy());
        return r;
    }

    void setDefaultDialect(String value) {
        this.defaultDialect = value;
    }

    // ── Manual override ("dialect chennai", "சென்னை தமிழ்") ───────────────────────

    private static final Pattern DIALECT_COMMAND =
        Pattern.compile("^dialect\\s*:?\\s*(chennai|madurai|nellai|kongu|standard)$");

    private static final Map<String, String> TAMIL_COMMANDS = Map.of(
        "சென்னை தமிழ்", "CHENNAI",
        "மதுரை தமிழ்", "MADURAI",
        "நெல்லை தமிழ்", "NELLAI",
        "கொங்கு தமிழ்", "KONGU");

    /** The dialect code the message asks for, when the whole message is an override command. */
    public static Optional<String> parseOverrideCommand(String text) {
        if (text == null) return Optional.empty();
        String n = text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replaceAll("[.!]+$", "");
        Matcher m = DIALECT_COMMAND.matcher(n);
        if (m.matches()) return Optional.of(m.group(1).toUpperCase(Locale.ROOT));
        return Optional.ofNullable(TAMIL_COMMANDS.get(n));
    }

    /** The result for a farmer who chose a dialect. Logs the usual one line. */
    public static DialectResult overrideResult(String code) {
        return finish(result(code, 100, SOURCE_OVERRIDE));
    }

    /** Short Tamil confirmation of the chosen dialect. */
    public static String overrideConfirmation(String code) {
        String name = switch (code) {
            case "CHENNAI" -> "சென்னை தமிழில்";
            case "MADURAI" -> "மதுரை தமிழில்";
            case "NELLAI"  -> "நெல்லை தமிழில்";
            case "KONGU"   -> "கொங்கு தமிழில்";
            default        -> "எளிய தமிழில்";
        };
        return "சரி, இனி " + name + " பதில் சொல்கிறேன். 🗣️";
    }

    public String buildAnswerPrompt(String question, DialectResult dialect, String language) {
        if ("english".equals(language)) {
            return "You are an expert agricultural advisor for Tamil Nadu. Answer in English only. " +
                   "Farmer question: " + question;
        }
        return switch (dialect.getDialect()) {
            case "KONGU" ->
                "நீங்கள் ஹோசூர் மற்றும் கோயம்புத்தூர் பகுதியின் வேளாண் நிபுணர்.\n" +
                "கொங்கு வட்டார தமிழ் மட்டுமே பயன்படுத்தவும் — ஆகுது, வேணும், பண்ணலாம், " +
                "நல்லாருக்கு, சரியாகுது, போட்டுக்கோங்க, பாருங்க, கொடுங்க.\n" +
                "விவசாயி கேள்வி: " + question;

            case "MADURAI" ->
                "நீங்கள் மதுரை பகுதியின் வேளாண் நிபுணர்.\n" +
                "மதுரை வட்டார தமிழ் மட்டுமே பயன்படுத்தவும் — ஆகுதுங்க, சொல்லுங்க, " +
                "குடுங்க, என்னாங்க, பாருங்க, செய்யுங்க, வாங்குங்க, போங்க, " +
                "தெரியுமாங்க, வந்திருக்காங்க.\n" +
                "விவசாயி கேள்வி: " + question;

            case "NELLAI" ->
                "நீங்கள் திருநெல்வேலி பகுதியின் வேளாண் நிபுணர்.\n" +
                "நெல்லை வட்டார தமிழ் மட்டுமே பயன்படுத்தவும் — செய்யணும்டா, ஆகும்டா, " +
                "பாருடா, சரியில்லடா, போட்டுடு, தெரியுமா, வந்துட்டான், போயிட்டான், " +
                "கொடுட்டான், நல்லாருக்கா.\n" +
                "விவசாயி கேள்வி: " + question;

            case "CHENNAI" ->
                "நீங்கள் சென்னை பகுதியின் வேளாண் நிபுணர்.\n" +
                "சென்னை வட்டார தமிழ் மட்டுமே பயன்படுத்தவும் — மச்சான், டா, என்னமா, " +
                "போயிட்டு, வந்துட்டே, சொல்லுடா, பாருடா, தெரியுமாடா.\n" +
                "விவசாயி கேள்வி: " + question;

            default ->
                "நீங்கள் தமிழ்நாடு வேளாண் நிபுணர்.\n" +
                "தெளிவான எளிய தமிழில் பதில் சொல்லவும்.\n" +
                "விவசாயி கேள்வி: " + question;
        };
    }
}
