package com.coreissuer.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CryptoUtils {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HASH_ALGORITHM = "SHA-256";

    private CryptoUtils() {
        // utility class
    }

    /**
     * Keyed hash for data at rest (PAN). HMAC-SHA256 rather than SHA-256(value + pepper):
     * the pepper acts as a proper secret key, which resists length-extension and offline
     * brute force of the low-entropy input space. Hex output is 64 chars — matches CHAR(64).
     */
    public static String hashWithPepper(String rawValue, String pepper) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return toHex(mac.doFinal(rawValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(HMAC_ALGORITHM + " unavailable", e);
        }
    }

    /** Unkeyed content hash (idempotency request fingerprints). Hex output is 64 chars. */
    public static String simpleHash(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return toHex(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
