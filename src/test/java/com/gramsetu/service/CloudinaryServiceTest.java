package com.gramsetu.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CloudinaryServiceTest {

    private Cloudinary cloudinary;
    private Uploader uploader;
    private CloudinaryService service;

    @BeforeEach
    void setUp() {
        cloudinary = mock(Cloudinary.class);
        uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        service = new CloudinaryService(cloudinary);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map> captureParams() throws IOException {
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(any(), captor.capture());
        return captor;
    }

    @Test
    void successfulUpload_returnsTheSecureUrl() throws Exception {
        when(uploader.upload(any(), any(Map.class))).thenReturn(Map.of("secure_url", "https://cdn.example.test/a.mp3"));
        assertEquals("https://cdn.example.test/a.mp3", service.uploadAudio(new byte[]{1, 2, 3}, "abc"));
    }

    @Test
    void uploadSendsAudioAsVideoMp3WithTimeoutsAndSanitisedPublicId() throws Exception {
        when(uploader.upload(any(), any(Map.class))).thenReturn(Map.of("secure_url", "https://cdn.example.test/a.mp3"));
        service.uploadAudio(new byte[]{1}, "../weird name?.wav");
        Map<?, ?> params = captureParams().getValue();
        assertEquals("video", params.get("resource_type"));
        assertEquals("mp3", params.get("format"));
        assertEquals("gramsetu_audio____weird_name__wav", params.get("public_id"));   // "../" and "?." become underscores
        assertEquals(CloudinaryService.READ_TIMEOUT_MS, params.get("timeout"));
        assertEquals(CloudinaryService.CONNECT_TIMEOUT_MS, params.get("connect_timeout"));
    }

    @Test
    void networkFailure_returnsNull() throws Exception {
        when(uploader.upload(any(), any(Map.class))).thenThrow(new IOException("connect timed out"));
        assertNull(service.uploadAudio(new byte[]{1}, "abc"));
    }

    @Test
    void apiRejection_badCredentialsOrQuota_returnsNull() throws Exception {
        when(uploader.upload(any(), any(Map.class))).thenThrow(new RuntimeException("Invalid api_key"));
        assertNull(service.uploadAudio(new byte[]{1}, "abc"));
    }

    @Test
    void responseWithoutSecureUrl_returnsNull() throws Exception {
        when(uploader.upload(any(), any(Map.class))).thenReturn(Map.of("public_id", "x"));
        assertNull(service.uploadAudio(new byte[]{1}, "abc"));
    }

    @Test
    void nullResponse_returnsNull() throws Exception {
        when(uploader.upload(any(), any(Map.class))).thenReturn(null);
        assertNull(service.uploadAudio(new byte[]{1}, "abc"));
    }

    @Test
    void emptyOrNullAudio_isNotUploaded() throws Exception {
        assertNull(service.uploadAudio(new byte[0], "abc"));
        assertNull(service.uploadAudio(null, "abc"));
        verifyNoInteractions(uploader);
    }

    @Test
    void blankConfiguration_failsStartupNamingTheVariableNotItsValue() {
        IllegalStateException e1 = assertThrows(IllegalStateException.class,
                () -> new CloudinaryService("", "key", "secret"));
        assertTrue(e1.getMessage().contains("CLOUDINARY_CLOUD_NAME"));
        IllegalStateException e2 = assertThrows(IllegalStateException.class,
                () -> new CloudinaryService("cloud", " ", "secret"));
        assertTrue(e2.getMessage().contains("CLOUDINARY_API_KEY"));
        IllegalStateException e3 = assertThrows(IllegalStateException.class,
                () -> new CloudinaryService("cloud", "key", null));
        assertTrue(e3.getMessage().contains("CLOUDINARY_API_SECRET"));
        assertFalse(e3.getMessage().contains("secret"), "message must not echo any value");
    }

    @Test
    void credentialsNeverAppearInLogsEvenWhenTheSdkErrorMessageEchoesThem() throws Exception {
        String key = "KEY-0123456789";
        String secret = "SECRET-ABCDEFGHIJ";
        CloudinaryService masked = new CloudinaryService(cloudinary, key, secret);
        when(uploader.upload(any(), any(Map.class)))
                .thenThrow(new RuntimeException("Invalid credentials " + key + " / " + secret));

        Logger logger = (Logger) LoggerFactory.getLogger(CloudinaryService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertNull(masked.uploadAudio(new byte[]{1}, "abc"));
        } finally {
            logger.detachAppender(appender);
        }

        assertFalse(appender.list.isEmpty(), "the failure should be logged");
        for (ILoggingEvent event : appender.list) {
            String text = event.getFormattedMessage();
            assertFalse(text.contains(key), "API key leaked into a log line");
            assertFalse(text.contains(secret), "API secret leaked into a log line");
        }
        // only the exception class is logged, never the SDK message
        assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("RuntimeException")));
        assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("Invalid credentials")));
    }

    @Test
    void completeConfiguration_buildsTheService() {
        assertDoesNotThrow(() -> new CloudinaryService("cloud", "key", "secret"));
    }
}
