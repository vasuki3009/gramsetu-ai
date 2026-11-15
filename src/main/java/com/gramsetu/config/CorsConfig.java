package com.gramsetu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS policy driven entirely by configuration:
 *   CORS_ALLOWED_ORIGINS         — comma-separated exact origins (e.g. the production frontend)
 *   CORS_ALLOWED_ORIGIN_PATTERNS — optional comma-separated patterns, e.g. this project's
 *                                  Vercel preview URLs. Never use a bare *.vercel.app.
 * With both empty, no cross-origin browser access is allowed.
 * The frontend does not send cookies, so credentials are not enabled.
 *
 * CORS runs as a servlet filter with the highest order of our filters (before the Twilio
 * signature check and the rate limiter). Spring MVC only adds CORS headers after the filter
 * chain, so a response written by a filter (429 Too Many Requests, 403 Forbidden) would
 * otherwise carry no CORS headers and the browser would hide its status and Retry-After
 * from the frontend. Retry-After is exposed so the frontend can show a wait time.
 */
@Configuration
public class CorsConfig {

    /** Runs before TwilioSignatureFilter (1) and RateLimitFilter (2). */
    public static final int FILTER_ORDER = 0;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(
            @Value("${cors.allowed-origins:}") String allowedOrigins,
            @Value("${cors.allowed-origin-patterns:}") String allowedOriginPatterns) {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(split(allowedOrigins));
        config.setAllowedOriginPatterns(split(allowedOriginPatterns));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.addUrlPatterns("/*");
        bean.setName("corsFilter");
        bean.setOrder(FILTER_ORDER);
        return bean;
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
