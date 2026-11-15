package com.gramsetu.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/** Independent implementation of Twilio's documented signing algorithm, for tests only. */
final class TwilioTestSigner {

    private TwilioTestSigner() {}

    /** base64(HMAC-SHA1(authToken, url + concat(sorted key + value))) */
    static String sign(String authToken, String url, Map<String, String> params) {
        try {
            StringBuilder data = new StringBuilder(url);
            new TreeMap<>(params).forEach((k, v) -> data.append(k).append(v));
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
