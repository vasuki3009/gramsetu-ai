package com.gramsetu.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CorsConfigTest.Ping.class)
@Import({CorsConfigTest.Ping.class, CorsConfig.class})
@TestPropertySource(properties = {
    "cors.allowed-origins=https://app.example.com, http://localhost:5173",
    "cors.allowed-origin-patterns=https://myapp-*-myteam.vercel.app"
})
class CorsConfigTest {

    @RestController
    static class Ping {
        @GetMapping("/ping")
        String ping() { return "ok"; }
    }

    @Autowired
    private MockMvc mockMvc;

    private void assertAllowed(String origin) throws Exception {
        mockMvc.perform(get("/ping").header("Origin", origin))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    private void assertBlocked(String origin) throws Exception {
        mockMvc.perform(get("/ping").header("Origin", origin))
                .andExpect(status().isForbidden());
    }

    @Test
    void exactConfiguredOriginsAreAllowed() throws Exception {
        assertAllowed("https://app.example.com");
        assertAllowed("http://localhost:5173");
    }

    @Test
    void projectPreviewPatternIsAllowed() throws Exception {
        assertAllowed("https://myapp-git-feature-myteam.vercel.app");
    }

    @Test
    void otherOriginsAreBlocked() throws Exception {
        assertBlocked("https://evil.example.com");
        assertBlocked("https://other-project.vercel.app");
        assertBlocked("https://myapp-git-feature-otherteam.vercel.app");
    }

    @Test
    void preflightIsAnsweredForAllowedOriginAndIsRefusedForOthers() throws Exception {
        mockMvc.perform(options("/ping")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"));
        mockMvc.perform(options("/ping")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void retryAfterIsExposedToBrowsers() throws Exception {
        mockMvc.perform(get("/ping").header("Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"));
    }

    @Test
    void credentialsAreNotEnabled() throws Exception {
        mockMvc.perform(get("/ping").header("Origin", "https://app.example.com"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }
}
