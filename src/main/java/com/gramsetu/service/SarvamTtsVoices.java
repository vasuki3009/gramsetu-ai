package com.gramsetu.service;

import java.util.Set;

/**
 * Sarvam bulbul:v3 speaker names and the dialect-to-speaker map: the ONE place voices are chosen.
 *
 * The old v2 speakers (anushka, abhilash, manisha, vidya, arya, karun) do NOT exist in bulbul:v3,
 * and a speaker that does not match the model is rejected. The valid names below come from Sarvam's
 * text-to-speech reference (they are lowercase and case-sensitive).
 *
 * The choices are made by name and gender only. Sarvam's page does not say which speakers sound best in Tamil,
 * so listen to each one and change the map if a voice does not suit the dialect.
 */
public final class SarvamTtsVoices {

    private SarvamTtsVoices() {}

    /** Speakers accepted by bulbul:v3. */
    public static final Set<String> V3_SPEAKERS = Set.of(
            "shubh", "aditya", "ritu", "priya", "neha", "rahul", "pooja", "rohan", "simran", "kavya", "amit", "dev",
            "ishita", "shreya", "ratan", "varun", "manan", "sumit", "roopa", "kabir", "aayan", "ashutosh", "advait",
            "anand", "tanya", "tarun", "sunny", "mani", "gokul", "vijay", "shruti", "suhani", "mohit", "kavitha",
            "rehan", "soham", "rupali");

    /** Sarvam's own default speaker; used when a name is missing or not valid for the model. */
    public static final String DEFAULT = "shubh";

    /** Dialect code (KONGU, MADURAI, NELLAI, CHENNAI, STANDARD, ...) to a valid v3 speaker. */
    public static String speakerFor(String dialect) {
        if (dialect == null || dialect.isBlank()) return "priya";
        return switch (dialect.trim().toLowerCase()) {
            case "kongu", "krishnagiri", "hosur", "coimbatore" -> "vijay";    // male, the farming community voice
            case "madurai", "southern", "south"                -> "gokul";    // male, firm
            case "nellai", "tirunelveli", "thoothukudi"        -> "roopa";    // female, softer
            default                                            -> "priya";    // chennai, madras, standard, unknown
        };
    }

    /** True when the name is a bulbul:v3 speaker. */
    public static boolean isValidV3(String speaker) {
        return speaker != null && V3_SPEAKERS.contains(speaker);
    }
}
