package com.gramsetu.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.gramsetu.util.LogSafe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Hosts TTS audio on Cloudinary so Twilio can deliver it to WhatsApp as media.
 *
 * Configuration comes only from CLOUDINARY_CLOUD_NAME / CLOUDINARY_API_KEY / CLOUDINARY_API_SECRET.
 * A blank value fails startup with a message naming the variable (never its value).
 * Uploads have connect/read timeouts so a slow Cloudinary cannot hang the reply thread.
 * The configured key and secret are masked in anything this class logs.
 */
@Service
@Slf4j
public class CloudinaryService {

    static final int CONNECT_TIMEOUT_MS = 10_000;
    static final int READ_TIMEOUT_MS = 30_000;

    private final Cloudinary cloudinary;

    // @Autowired is required: with two constructors Spring cannot pick one on its own and
    // would look for a (missing) default constructor.
    @Autowired
    public CloudinaryService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        // buildClient validates first, so apiKey/apiSecret are non-null when trimmed below
        this(buildClient(cloudName, apiKey, apiSecret), apiKey.trim(), apiSecret.trim());
    }

    /**
     * For tests: inject a pre-built (or mocked) client. {@code unusedSecrets} are not needed any more: an SDK error
     * message is never logged (only the exception class), so there is nothing to mask.
     */
    CloudinaryService(Cloudinary cloudinary, String... unusedSecrets) {
        this.cloudinary = cloudinary;
    }

    private static Cloudinary buildClient(String cloudName, String apiKey, String apiSecret) {
        require("CLOUDINARY_CLOUD_NAME", cloudName);
        require("CLOUDINARY_API_KEY", apiKey);
        require("CLOUDINARY_API_SECRET", apiSecret);
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName.trim(),
                "api_key",    apiKey.trim(),
                "api_secret", apiSecret.trim(),
                "secure",     true
        ));
    }

    private static void require(String envName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Cloudinary is not configured: environment variable " + envName + " is empty or missing.");
        }
    }

    /**
     * Uploads audio bytes and returns the secure public URL.
     *
     * @param audioBytes audio to upload (must not be null or empty)
     * @param filename   name hint used for the public_id; only [A-Za-z0-9_-] is kept
     * @return the HTTPS URL, or {@code null} when the upload failed for any reason (the cause is
     *         logged without credentials). Callers must handle null and fall back to text only.
     */
    public String uploadAudio(byte[] audioBytes, String filename) {
        if (audioBytes == null || audioBytes.length == 0) {
            log.warn("[Cloudinary] Nothing to upload (empty audio)");
            return null;
        }
        String publicId = "gramsetu_audio_" + sanitize(filename);

        Map<String, Object> params = new HashMap<>();
        params.put("resource_type", "video");           // Cloudinary stores audio under "video"
        params.put("public_id", publicId);
        params.put("format", "mp3");
        params.put("connect_timeout", CONNECT_TIMEOUT_MS);
        params.put("connection_request_timeout", CONNECT_TIMEOUT_MS);
        params.put("timeout", READ_TIMEOUT_MS);

        try {
            Map<?, ?> result = cloudinary.uploader().upload(audioBytes, params);
            Object url = result == null ? null : result.get("secure_url");
            if (!(url instanceof String secureUrl) || secureUrl.isBlank()) {
                log.error("[Cloudinary] Upload of {} returned no secure_url", publicId);
                return null;
            }
            log.info("[Cloudinary] Uploaded audio: public_id={} ({} bytes)", publicId, audioBytes.length);
            return secureUrl;
        } catch (IOException e) {
            // Network problem or timeout talking to Cloudinary
            log.error("[Cloudinary] Upload of {} failed (I/O): {}", publicId, describe(e));
            return null;
        } catch (RuntimeException e) {
            // The SDK reports API errors (bad credentials, quota, invalid file) as runtime exceptions
            log.error("[Cloudinary] Upload of {} rejected: {}", publicId, describe(e));
            return null;
        }
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) return "unnamed";
        String clean = name.replaceAll("[^A-Za-z0-9_-]", "_");
        return clean.length() > 100 ? clean.substring(0, 100) : clean;
    }

    /** Exception class (and HTTP status) only: the SDK message can carry the API error body. */
    private static String describe(Exception e) {
        return LogSafe.error(e);
    }
}
