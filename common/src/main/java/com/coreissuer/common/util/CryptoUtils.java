package com.coreissuer.common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class CryptoUtils {

    private CryptoUtils() {
        // utility class
    }

    public static String hashWithPepper(String rawValue, String pepper) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String valueToHash = rawValue + pepper;
            byte[] hashBytes = digest.digest(valueToHash.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public static String simpleHash(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
