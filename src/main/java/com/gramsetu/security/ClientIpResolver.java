package com.gramsetu.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Determines the client IP used for rate limiting.
 *
 * Each proxy appends the address it saw to X-Forwarded-For, so the left-most entries are
 * client-supplied and spoofable. With N trusted proxies in front of the app, the N-th entry
 * from the RIGHT is the real client. trustedProxyHops = 0 ignores the header entirely
 * (direct exposure / local development); the default of 1 suits a single platform proxy
 * such as Railway's. If the header has fewer entries than expected, the socket address is used.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request, int trustedProxyHops) {
        if (trustedProxyHops > 0) {
            String header = request.getHeader("X-Forwarded-For");
            if (header != null && !header.isBlank()) {
                String[] parts = header.split(",");
                if (parts.length >= trustedProxyHops) {
                    String ip = parts[parts.length - trustedProxyHops].trim();
                    if (!ip.isEmpty()) return ip;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
