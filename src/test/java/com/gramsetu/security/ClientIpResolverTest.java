package com.gramsetu.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    private MockHttpServletRequest request(String xff) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("10.0.0.9");
        if (xff != null) r.addHeader("X-Forwarded-For", xff);
        return r;
    }

    @Test
    void singleTrustedProxy_usesRightmostEntry_notTheSpoofableLeftmost() {
        assertEquals("203.0.113.7", ClientIpResolver.resolve(request("6.6.6.6, 203.0.113.7"), 1));
    }

    @Test
    void twoTrustedProxies_usesSecondFromRight() {
        assertEquals("203.0.113.7", ClientIpResolver.resolve(request("203.0.113.7, 10.1.1.1"), 2));
    }

    @Test
    void zeroHops_ignoresTheHeader() {
        assertEquals("10.0.0.9", ClientIpResolver.resolve(request("1.2.3.4"), 0));
    }

    @Test
    void missingOrShortHeader_fallsBackToSocketAddress() {
        assertEquals("10.0.0.9", ClientIpResolver.resolve(request(null), 1));
        assertEquals("10.0.0.9", ClientIpResolver.resolve(request("1.2.3.4"), 2));
        assertEquals("10.0.0.9", ClientIpResolver.resolve(request("  "), 1));
    }
}
