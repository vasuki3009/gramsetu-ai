package com.gramsetu.config;

import com.gramsetu.security.FixedWindowRateLimiter;
import com.gramsetu.security.RateLimitFilter;
import com.gramsetu.security.TwilioSignatureFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the security servlet filters and the URL patterns they protect. */
@Configuration
public class SecurityFilterConfig {

    public static final String WHATSAPP_WEBHOOK_PATH = "/api/whatsapp/webhook";

    /** Endpoints that call Gemini/Groq/Sarvam. The Twilio webhook is excluded: it is signed. */
    static final String DEFAULT_RATE_LIMITED_PATHS =
            "/api/v1/farmer/ask,/api/v1/farmer/voice,/api/voice/process,/api/v1/crop/analyze,"
          + "/api/v1/crops/calendar/plan,/api/v1/schemes/find";

    /**
     * Twilio signature validation for the WhatsApp webhook.
     * TWILIO_VALIDATE_SIGNATURE defaults to true; set it to false only for local testing
     * with curl. When enabled, the token and public base URL are mandatory (fail fast).
     */
    @Bean
    public FilterRegistrationBean<TwilioSignatureFilter> twilioSignatureFilter(
            @Value("${twilio.auth.token:}") String authToken,
            @Value("${app.public.base.url:}") String publicBaseUrl,
            @Value("${twilio.validate-signature:true}") boolean validateSignature) {

        if (validateSignature && (authToken.isBlank() || publicBaseUrl.isBlank())) {
            throw new IllegalStateException(
                    "Twilio signature validation is enabled but TWILIO_AUTH_TOKEN or APP_PUBLIC_BASE_URL "
                  + "is empty. Set both (APP_PUBLIC_BASE_URL must be the exact public URL Twilio calls), "
                  + "or set TWILIO_VALIDATE_SIGNATURE=false for local development only.");
        }

        FilterRegistrationBean<TwilioSignatureFilter> bean = new FilterRegistrationBean<>(
                new TwilioSignatureFilter(authToken, publicBaseUrl, validateSignature));
        bean.addUrlPatterns(WHATSAPP_WEBHOOK_PATH);
        bean.setName("twilioSignatureFilter");
        bean.setOrder(1);
        return bean;
    }

    /**
     * Per-IP rate limit on the endpoints that call paid AI APIs.
     *   RATE_LIMIT_ENABLED            (default true)
     *   RATE_LIMIT_PER_MINUTE         (default 20 requests per IP per minute, shared by all paths below)
     *   RATE_LIMIT_TRUSTED_PROXY_HOPS (default 1; proxies in front of the app whose X-Forwarded-For
     *                                  entry is trusted; 0 = ignore the header)
     * The protected paths default to DEFAULT_RATE_LIMITED_PATHS (property ratelimit.paths overrides).
     *
     * DEPLOYMENT ASSUMPTION (X-Forwarded-For): the "client" is identified by the address a
     * trusted reverse proxy recorded in X-Forwarded-For. The app is expected to run behind
     * exactly RATE_LIMIT_TRUSTED_PROXY_HOPS such proxies (1 = a single platform edge proxy such
     * as Railway's, which appends the real client address). The Nth entry from the RIGHT is
     * used because the left-most entries are client-supplied and can be forged.
     *   - Reachable directly from the internet (no proxy)?  Set RATE_LIMIT_TRUSTED_PROXY_HOPS=0,
     *     otherwise a client can send its own X-Forwarded-For and dodge the limit.
     *   - More proxies than configured (e.g. CDN + platform proxy)?  Raise the value, otherwise
     *     every client behind the same proxy shares one budget.
     * See ClientIpResolver for the exact rule and the README for the deployment notes.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            @Value("${ratelimit.enabled:true}") boolean enabled,
            @Value("${ratelimit.requests-per-minute:20}") int requestsPerMinute,
            @Value("${ratelimit.trusted-proxy-hops:1}") int trustedProxyHops,
            @Value("${ratelimit.paths:" + DEFAULT_RATE_LIMITED_PATHS + "}") String[] paths) {

        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(
                new RateLimitFilter(new FixedWindowRateLimiter(Math.max(1, requestsPerMinute)), trustedProxyHops));
        bean.addUrlPatterns(paths);
        bean.setName("rateLimitFilter");
        bean.setEnabled(enabled);
        bean.setOrder(2);
        return bean;
    }
}
