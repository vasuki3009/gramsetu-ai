package com.gramsetu.util;

/** Normalisation helpers for untrusted request parameters. */
public final class InputSanitizer {

    /** farmer_queries.location is VARCHAR(100). */
    public static final int MAX_LOCATION_LENGTH = 100;

    private InputSanitizer() {}

    /** Only "english" is special; anything else (including null or junk) is the default Tamil. */
    public static String normalizeLanguage(String language) {
        return language != null && "english".equalsIgnoreCase(language.trim()) ? "english" : "tamil";
    }

    /** Trims, applies the default when blank, and caps the length to fit the DB column. */
    public static String limitLocation(String location, String defaultValue) {
        if (location == null || location.isBlank()) return defaultValue;
        String trimmed = location.trim();
        return trimmed.length() > MAX_LOCATION_LENGTH ? trimmed.substring(0, MAX_LOCATION_LENGTH) : trimmed;
    }

    public static boolean tooLong(String text, int max) {
        return text != null && text.length() > max;
    }
}
