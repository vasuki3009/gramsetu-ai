package com.gramsetu.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Per-client-IP rate limit for the endpoints that call paid AI APIs. Responds 429 with a
 * Retry-After header when the limit is exceeded. CORS pre-flight (OPTIONS) is not counted.
 * Note: this runs before Spring MVC's CORS handling, so browsers report a 429 as a network
 * error; the Retry-After header and JSON body are still visible to non-browser clients.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final FixedWindowRateLimiter limiter;
    private final int trustedProxyHops;

    public RateLimitFilter(FixedWindowRateLimiter limiter, int trustedProxyHops) {
        this.limiter = limiter;
        this.trustedProxyHops = trustedProxyHops;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String client = ClientIpResolver.resolve(request, trustedProxyHops);
        long retryAfter = limiter.tryAcquire(client);
        if (retryAfter > 0) {
            log.warn("[RateLimit] Limit exceeded for {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"rate_limited\","
                    + "\"message\":\"Too many requests. Please wait and try again.\","
                    + "\"retryAfterSeconds\":" + retryAfter + "}");
            return;
        }
        chain.doFilter(request, response);
    }
}
