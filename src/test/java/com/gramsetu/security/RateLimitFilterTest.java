package com.gramsetu.security;

import com.gramsetu.config.CorsConfig;
import com.gramsetu.config.SecurityFilterConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Limit of 2 requests per minute per client, one trusted proxy hop. */
@WebMvcTest(RateLimitFilterTest.Api.class)
@Import({RateLimitFilterTest.Api.class, SecurityFilterConfig.class, CorsConfig.class})
@TestPropertySource(properties = {
    "twilio.validate-signature=false",
    "cors.allowed-origins=https://app.example.com",
    "ratelimit.requests-per-minute=2",
    "ratelimit.trusted-proxy-hops=1"
})
class RateLimitFilterTest {

    @RestController
    static class Api {
        @PostMapping("/api/v1/farmer/ask")  String ask()     { return "ok"; }
        @PostMapping("/api/v1/crop/analyze") String analyze() { return "ok"; }
        @GetMapping("/api/v1/schemes/all")  String schemes() { return "ok"; }
    }

    @Autowired
    private MockMvc mvc;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder ask(String xff) {
        return post("/api/v1/farmer/ask").header("X-Forwarded-For", xff);
    }

    @Test
    void thirdRequestFromTheSameClientGets429WithRetryAfter() throws Exception {
        mvc.perform(ask("203.0.113.1")).andExpect(status().isOk());
        mvc.perform(ask("203.0.113.1")).andExpect(status().isOk());
        mvc.perform(ask("203.0.113.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void the429CarriesCorsHeadersSoTheBrowserCanReadIt() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(ask("203.0.113.20").header("Origin", "https://app.example.com")).andExpect(status().isOk());
        }
        mvc.perform(ask("203.0.113.20").header("Origin", "https://app.example.com"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void requestsFromDisallowedOriginsAreRefusedBeforeTheyConsumeBudget() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(ask("203.0.113.21").header("Origin", "https://evil.example.com"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(ask("203.0.113.21")).andExpect(status().isOk());
    }

    @Test
    void differentClientsHaveSeparateBudgets() throws Exception {
        for (int i = 0; i < 3; i++) mvc.perform(ask("203.0.113.2"));
        mvc.perform(ask("203.0.113.3")).andExpect(status().isOk());
    }

    @Test
    void spoofingTheLeftmostForwardedForEntryDoesNotBypassTheLimit() throws Exception {
        mvc.perform(ask("1.1.1.1, 203.0.113.4")).andExpect(status().isOk());
        mvc.perform(ask("2.2.2.2, 203.0.113.4")).andExpect(status().isOk());
        mvc.perform(ask("3.3.3.3, 203.0.113.4")).andExpect(status().isTooManyRequests());
    }

    @Test
    void limitIsSharedAcrossTheProtectedEndpoints() throws Exception {
        mvc.perform(ask("203.0.113.5")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/crop/analyze").header("X-Forwarded-For", "203.0.113.5")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/crop/analyze").header("X-Forwarded-For", "203.0.113.5")).andExpect(status().isTooManyRequests());
    }

    @Test
    void preflightRequestsAreNotCounted() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(options("/api/v1/farmer/ask")
                    .header("X-Forwarded-For", "203.0.113.6")
                    .header("Origin", "https://app.example.com")
                    .header("Access-Control-Request-Method", "POST"));
        }
        mvc.perform(ask("203.0.113.6")).andExpect(status().isOk());
    }

    @Test
    void unprotectedEndpointsAreNeverLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/v1/schemes/all").header("X-Forwarded-For", "203.0.113.7"))
                    .andExpect(status().isOk());
        }
    }
}
