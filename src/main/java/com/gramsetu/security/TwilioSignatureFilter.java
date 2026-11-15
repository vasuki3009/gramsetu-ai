package com.gramsetu.security;

import com.twilio.security.RequestValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Rejects WhatsApp webhook requests that were not signed by Twilio.
 *
 * Twilio signs every request with HMAC-SHA1(authToken, fullUrl + sortedParams) and sends the
 * result in the X-Twilio-Signature header. The URL used for validation is built from the
 * configured public base URL (APP_PUBLIC_BASE_URL), not from the incoming request, because
 * behind a reverse proxy the request's own scheme/host/port do not match what Twilio called.
 *
 * Fails closed: a missing or invalid header yields 403 before any controller code runs.
 */
@Slf4j
public class TwilioSignatureFilter extends OncePerRequestFilter {

    public static final String SIGNATURE_HEADER = "X-Twilio-Signature";

    private final RequestValidator validator;
    private final String publicBaseUrl;
    private final boolean enabled;

    public TwilioSignatureFilter(String authToken, String publicBaseUrl, boolean enabled) {
        // Built only when enabled: RequestValidator rejects an empty key.
        this.validator = enabled ? new RequestValidator(authToken) : null;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
        this.enabled = enabled;
        if (!enabled) {
            log.warn("[Security] Twilio signature validation is DISABLED (TWILIO_VALIDATE_SIGNATURE=false). "
                   + "Never run like this in production: anyone can call the WhatsApp webhook.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        String signature = request.getHeader(SIGNATURE_HEADER);
        if (signature == null || signature.isBlank()) {
            reject(response, "missing " + SIGNATURE_HEADER + " header");
            return;
        }

        String url = publicBaseUrl + request.getRequestURI()
                + (request.getQueryString() != null ? "?" + request.getQueryString() : "");

        Map<String, String> params = new TreeMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v.length > 0 ? v[0] : ""));

        boolean valid;
        try {
            valid = validator.validate(url, params, signature);
        } catch (Exception e) {
            valid = false;
        }
        if (!valid) {
            reject(response, "signature mismatch");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String reason) throws IOException {
        // Reason only; never log the signature, token or request parameters.
        log.warn("[Security] Rejected WhatsApp webhook call: {}", reason);
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
    }
}
