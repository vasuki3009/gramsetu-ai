package com.gramsetu.security;

import com.gramsetu.config.SecurityFilterConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Signature validation enabled (the default). */
@WebMvcTest(TwilioSignatureFilterTest.Hook.class)
@Import({TwilioSignatureFilterTest.Hook.class, SecurityFilterConfig.class})
@TestPropertySource(properties = {
    "twilio.auth.token=test-token-1234567890",
    "app.public.base.url=https://api.example.test/",   // trailing slash must be tolerated
    "twilio.validate-signature=true"
})
class TwilioSignatureFilterTest {

    static final String TOKEN = "test-token-1234567890";
    static final String URL   = "https://api.example.test/api/whatsapp/webhook";

    @RestController
    static class Hook {
        @PostMapping("/api/whatsapp/webhook")
        String webhook(@RequestParam("Body") String body) { return "handled:" + body; }

        @GetMapping("/api/whatsapp/health")
        String health() { return "up"; }
    }

    @Autowired
    private MockMvc mockMvc;

    private static final Map<String, String> PARAMS =
            Map.of("Body", "hello", "From", "whatsapp:+10000000000", "NumMedia", "0");

    private MockHttpServletRequestBuilder request(Map<String, String> sent) {
        MockHttpServletRequestBuilder b = post("/api/whatsapp/webhook");
        sent.forEach(b::param);
        return b;
    }

    @Test
    void validSignature_isAccepted() throws Exception {
        String sig = TwilioTestSigner.sign(TOKEN, URL, PARAMS);
        mockMvc.perform(request(PARAMS).header("X-Twilio-Signature", sig))
                .andExpect(status().isOk())
                .andExpect(content().string("handled:hello"));
    }

    @Test
    void invalidSignature_isRejectedWith403() throws Exception {
        mockMvc.perform(request(PARAMS).header("X-Twilio-Signature", "AAAAAAAAAAAAAAAAAAAAAAAAAAA="))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingSignatureHeader_isRejectedWith403() throws Exception {
        mockMvc.perform(request(PARAMS))
                .andExpect(status().isForbidden());
    }

    @Test
    void blankSignatureHeader_isRejectedWith403() throws Exception {
        mockMvc.perform(request(PARAMS).header("X-Twilio-Signature", "  "))
                .andExpect(status().isForbidden());
    }

    @Test
    void signatureFromAnotherToken_isRejected() throws Exception {
        String sig = TwilioTestSigner.sign("some-other-token", URL, PARAMS);
        mockMvc.perform(request(PARAMS).header("X-Twilio-Signature", sig))
                .andExpect(status().isForbidden());
    }

    @Test
    void tamperedParameter_isRejected() throws Exception {
        String sig = TwilioTestSigner.sign(TOKEN, URL, PARAMS);
        Map<String, String> tampered = Map.of("Body", "attacker text", "From", "whatsapp:+10000000000", "NumMedia", "0");
        mockMvc.perform(request(tampered).header("X-Twilio-Signature", sig))
                .andExpect(status().isForbidden());
    }

    @Test
    void signatureForDifferentHost_isRejected() throws Exception {
        String sig = TwilioTestSigner.sign(TOKEN, "https://evil.example.test/api/whatsapp/webhook", PARAMS);
        mockMvc.perform(request(PARAMS).header("X-Twilio-Signature", sig))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherEndpoints_areNotAffected() throws Exception {
        mockMvc.perform(get("/api/whatsapp/health"))
                .andExpect(status().isOk());
    }

    @Test
    void enabledWithoutTokenOrBaseUrl_failsFast() {
        SecurityFilterConfig config = new SecurityFilterConfig();
        assertThrows(IllegalStateException.class, () -> config.twilioSignatureFilter("", "https://x.test", true));
        assertThrows(IllegalStateException.class, () -> config.twilioSignatureFilter("token", " ", true));
    }
}
