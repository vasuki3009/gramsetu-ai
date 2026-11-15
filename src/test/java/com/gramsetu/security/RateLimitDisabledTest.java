package com.gramsetu.security;

import com.gramsetu.config.SecurityFilterConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RATE_LIMIT_ENABLED=false turns the limiter off. */
@WebMvcTest(RateLimitDisabledTest.Api.class)
@Import({RateLimitDisabledTest.Api.class, SecurityFilterConfig.class})
@TestPropertySource(properties = {
    "twilio.validate-signature=false",
    "ratelimit.enabled=false",
    "ratelimit.requests-per-minute=1"
})
class RateLimitDisabledTest {

    @RestController
    static class Api {
        @PostMapping("/api/v1/farmer/ask") String ask() { return "ok"; }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void disabledLimiterNeverBlocks() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/farmer/ask")).andExpect(status().isOk());
        }
    }
}
