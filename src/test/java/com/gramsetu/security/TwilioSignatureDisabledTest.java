package com.gramsetu.security;

import com.gramsetu.config.SecurityFilterConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TWILIO_VALIDATE_SIGNATURE=false (local development only): no header needed, no config required. */
@WebMvcTest(TwilioSignatureDisabledTest.Hook.class)
@Import({TwilioSignatureDisabledTest.Hook.class, SecurityFilterConfig.class})
@TestPropertySource(properties = "twilio.validate-signature=false")
class TwilioSignatureDisabledTest {

    @RestController
    static class Hook {
        @PostMapping("/api/whatsapp/webhook")
        String webhook(@RequestParam("Body") String body) { return "handled:" + body; }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void disabledFlag_allowsUnsignedRequests() throws Exception {
        mockMvc.perform(post("/api/whatsapp/webhook").param("Body", "hi"))
                .andExpect(status().isOk())
                .andExpect(content().string("handled:hi"));
    }

    @Test
    void disabledFlag_doesNotRequireTokenOrBaseUrl() {
        assertDoesNotThrow(() -> new SecurityFilterConfig().twilioSignatureFilter("", "", false));
    }
}
