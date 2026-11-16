package com.dt124.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 helpers used for deduplication, data manifests and attachment hashing.
 */
public final class HashUtil {

    private HashUtil() {
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static String sha256Hex(String data) {
        return sha256Hex(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Deduplication key based on normalized subject + body (see REQUIREMENTS section 4).
     */
    public static String contentKey(String subject, String body) {
        String normalizedSubject = normalize(subject);
        String normalizedBody = normalize(body);
        return sha256Hex(normalizedSubject + "\n\u0000\n" + normalizedBody);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim().toLowerCase(java.util.Locale.ROOT);
    }
}
