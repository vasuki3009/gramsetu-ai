package com.gramsetu.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the redaction pattern in logback-spring.xml (the real pattern string is
 * read from the file) strips URLs and key=/token= values from messages and stack traces.
 */
class LogRedactionTest {

    private PatternLayout layout;
    private final LoggerContext ctx = new LoggerContext();

    @BeforeEach
    void setUp() throws IOException {
        String xml = new String(new ClassPathResource("logback-spring.xml")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("<pattern>(.*?)</pattern>", Pattern.DOTALL).matcher(xml);
        assertTrue(m.find(), "pattern element not found");
        String pattern = m.group(1)
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        layout = new PatternLayout();
        layout.setContext(ctx);
        layout.setPattern(pattern);
        layout.start();
    }

    private String format(String message, Throwable t) {
        LoggingEvent ev = new LoggingEvent(LoggerFactory.getLogger("x").getName(),
                ctx.getLogger("x"), Level.ERROR, message, t, null);
        return layout.doLayout(ev);
    }

    @Test
    void urlsAndKeysInMessagesAreRedacted() {
        String out = format("POST https://api.example.com/v1/x?key=SECRETVALUE123 failed; token=abc999 ok", null);
        assertFalse(out.contains("SECRETVALUE123"), out);
        assertFalse(out.contains("https://"), out);
        assertFalse(out.contains("abc999"), out);
        assertTrue(out.contains("<url>"), out);
    }

    @Test
    void urlsInExceptionMessagesAndStackTracesAreRedacted() {
        Exception e = new ResourceAccessException(
                "I/O error on POST request for \"https://api.example.com/v1/x?key=SECRETVALUE123\": timeout");
        String out = format("call failed", e);
        assertFalse(out.contains("SECRETVALUE123"), out);
        assertFalse(out.contains("https://api.example.com"), out);
        assertTrue(out.contains("ResourceAccessException"), "stack trace should still be logged: " + out);
    }

    @Test
    void ordinaryMessagesAreUnchanged() {
        assertTrue(format("Downloaded 1024 bytes of media", null).contains("Downloaded 1024 bytes of media"));
    }
}
