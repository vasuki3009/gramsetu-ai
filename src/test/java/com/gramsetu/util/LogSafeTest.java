package com.gramsetu.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LogSafeTest {

    @Test
    void phoneKeepsPrefixCountryCodeAndLastTwoDigitsOnly() {
        String masked = LogSafe.phone("whatsapp:+910000000000");
        assertEquals("whatsapp:+91********00", masked);
        assertFalse(masked.contains("0000000"));
    }

    @Test
    void errorGivesClassAndHttpStatusButNeverMessageOrBody() {
        String echoed = "my tomato leaves are curling";
        HttpClientErrorException http = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests " + echoed, HttpHeaders.EMPTY,
                ("{\"error\":\"" + echoed + "\"}").getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        String out = LogSafe.error(http);
        // Spring creates a per-status subclass (TooManyRequests), so the class name is that subclass
        assertEquals(http.getClass().getSimpleName() + " (HTTP 429)", out);
        assertFalse(out.contains(echoed));

        HttpServerErrorException server = HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, echoed, HttpHeaders.EMPTY, echoed.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        assertEquals(server.getClass().getSimpleName() + " (HTTP 502)", LogSafe.error(server));
        assertFalse(LogSafe.error(server).contains(echoed));
    }

    @Test
    void errorForOtherExceptionsIsClassNameOnly() {
        String out = LogSafe.error(new IllegalStateException("நெல் பூச்சி question text", new RuntimeException("cause text")));
        assertEquals("IllegalStateException", out);
        assertEquals("unknown error", LogSafe.error(null));
    }

    @Test
    void phoneWithoutPrefix() {
        assertEquals("+91********00", LogSafe.phone("+910000000000"));
    }

    @Test
    void shortOrMissingValuesNeverLeakAnything() {
        assertEquals("unknown", LogSafe.phone(null));
        assertEquals("unknown", LogSafe.phone("  "));
        assertEquals("whatsapp:+***", LogSafe.phone("whatsapp:+123"));
    }

    @Test
    void lenHandlesNull() {
        assertEquals(0, LogSafe.len(null));
        assertEquals(5, LogSafe.len("hello"));
    }
}
