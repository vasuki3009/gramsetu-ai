package com.gramsetu.util;

import org.springframework.web.client.RestClientResponseException;

/**
 * Helpers for logging without leaking farmers' data. At INFO the application logs lengths,
 * ids and masked phone numbers only; the full text of questions, transcripts and answers is
 * logged at DEBUG, which only the "dev" profile enables for com.gramsetu.
 */
public final class LogSafe {

    private LogSafe() {}

    /** Length of the text, or 0 for null; log this instead of the text itself. */
    public static int len(String text) {
        return text == null ? 0 : text.length();
    }

    /**
     * A log-safe description of an exception: its class name, plus the HTTP status code for HTTP client/server
     * errors ("HttpClientErrorException (HTTP 429)"). Never the message, the cause or the response body, because
     * those can echo the farmer's text.
     */
    public static String error(Throwable e) {
        if (e == null) return "unknown error";
        String name = e.getClass().getSimpleName();
        if (e instanceof RestClientResponseException http) {
            return name + " (HTTP " + http.getStatusCode().value() + ")";
        }
        return name;
    }

    /**
     * Masks a phone number such as "whatsapp:+910000000000" to "whatsapp:+91********00":
     * keeps the "whatsapp:" prefix, the first two and the last two digits.
     */
    public static String phone(String raw) {
        if (raw == null || raw.isBlank()) return "unknown";
        String prefix = raw.startsWith("whatsapp:") ? "whatsapp:" : "";
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() <= 4) return prefix + "+***";
        return prefix + "+" + digits.substring(0, 2)
                + "*".repeat(digits.length() - 4)
                + digits.substring(digits.length() - 2);
    }
}
