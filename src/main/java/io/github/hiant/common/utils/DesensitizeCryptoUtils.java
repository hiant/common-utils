package io.github.hiant.common.utils;

import java.nio.charset.StandardCharsets;

/**
 * Crypto helpers for {@link DesensitizeAction#ENCRYPT}.
 * <p>
 * This class defines a small reversible ciphertext format intended for controlled troubleshooting.
 *
 * <b>Format</b>
 * <p>
 * AES-GCM (recommended):
 * {@code ENC[v1,kid=<keyId>,alg=AESGCM]::<base64Payload>}
 * <ul>
 * <li>{@code v1}: version</li>
 * <li>{@code kid}: key id</li>
 * <li>{@code alg}: {@code AESGCM}</li>
 * <li>{@code base64Payload}: Base64(nonce || ciphertext+tag) as produced by
 * {@link CipherUtils#encryptWithAESGCM(String, byte[], byte[])}</li>
 * </ul>
 * <p>
 * AES-CBC (compatibility):
 * {@code ENC[v1,kid=<keyId>,alg=AESCBC,iv=<iv>]::<base64Ciphertext>}
 * <ul>
 * <li>{@code alg}: {@code AESCBC}</li>
 * <li>{@code iv}: IV string, passed to {@link CipherUtils#encryptWithAES(String, String, String)}</li>
 * </ul>
 *
 * @since JDK1.8
 */
public final class DesensitizeCryptoUtils {

    private static final String PREFIX     = "ENC[";
    private static final String SUFFIX     = "]::";
    private static final String VERSION_V1 = "v1";
    private static final String ALG_AESGCM = "AESGCM";
    private static final String ALG_AESCBC = "AESCBC";
    private static final String PARAM_IV   = "iv";
    private static final String PARAM_KID  = "kid";
    private static final String PARAM_ALG  = "alg";

    private DesensitizeCryptoUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Encrypt a raw value for safe string rendering.
     *
     * @param rawValue
     *            raw value
     * @param aad
     *            additional authenticated data (nullable)
     * @return formatted ciphertext, or null when rawValue is null
     */
    public static String encryptForToString(String rawValue, byte[] aad) {
        if (rawValue == null) {
            return null;
        }

        byte[] key = requireKey();
        return CipherUtils.encryptWithAESGCM(rawValue, key, aad);
    }

    public static String encryptForToString(String rawValue, byte[] key, byte[] iv) {
        if (rawValue == null) {
            return null;
        }

        return CipherUtils.encryptWithAES(rawValue, key, iv);
    }

    /**
     * Decrypt a formatted ciphertext produced by {@link #encryptForToString(String, byte[])}.
     *
     * @param encText
     *            formatted ciphertext
     * @param aad
     *            additional authenticated data (nullable)
     * @return plaintext, or null if encText is null
     */
    public static String decryptFromToString(String encText, byte[] aad) {
        if (encText == null) {
            return null;
        }
        Parsed parsed = parse(encText);
        if (!VERSION_V1.equals(parsed.version)) {
            throw new IllegalArgumentException("Unsupported ENC version: " + parsed.version);
        }

        if (ALG_AESGCM.equals(parsed.alg)) {
            byte[] key = requireKey();
            return CipherUtils.decryptWithAESGCM(parsed.payloadBase64, key, aad);
        }

        if (ALG_AESCBC.equals(parsed.alg)) {
            if (parsed.iv == null || parsed.iv.isEmpty()) {
                throw new IllegalArgumentException("Invalid ENC header: missing iv for AESCBC");
            }
            byte[] keyBytes = requireKey();
            String derivedSecretKey = new String(keyBytes, StandardCharsets.UTF_8);
            return CipherUtils.decryptWithAES(parsed.payloadBase64, derivedSecretKey, parsed.iv);
        }

        throw new IllegalArgumentException("Unsupported ENC alg: " + parsed.alg);
    }

    /**
     * Decrypt a formatted ciphertext using field-bound AAD.
     *
     * @param encText
     *            formatted ciphertext
     * @param declaringClass
     *            declaring class
     * @param fieldName
     *            field name
     * @return plaintext, or null if encText is null
     */
    public static String decryptFromToString(String encText, Class<?> declaringClass, String fieldName) {
        return decryptFromToString(encText, toStringAad(declaringClass, fieldName));
    }

    /**
     * Convenience overload without AAD.
     *
     * @param rawValue
     *            raw value
     * @return formatted ciphertext, or null when rawValue is null
     */
    public static String encryptForToString(String rawValue) {
        return encryptForToString(rawValue, (byte[]) null);
    }

    /**
     * Convenience overload without AAD.
     *
     * @param encText
     *            formatted ciphertext
     * @return plaintext, or null when encText is null
     */
    public static String decryptFromToString(String encText) {
        return decryptFromToString(encText, (byte[]) null);
    }

    /**
     * Derive a stable AAD for toString encryption from class + field.
     * <p>
     * This is optional but recommended when you want to bind ciphertext to a specific field name.
     *
     * @param declaringClass
     *            declaring class
     * @param fieldName
     *            field name
     * @return aad bytes, or null if any argument is null
     */
    public static byte[] toStringAad(Class<?> declaringClass, String fieldName) {
        if (declaringClass == null || fieldName == null) {
            return null;
        }
        return (declaringClass.getName() + "#" + fieldName).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] requireKey() {
        byte[] key = DesensitizeCryptoProviders.getProvider().key();
        if (key == null) {
            throw new IllegalStateException("AES key not found.");
        }
        if (!DesensitizeCryptoProviders.isSupportedAesKeyLength(key)) {
            throw new IllegalStateException("Invalid AES key length " + key.length + ", expected 16/24/32 bytes");
        }
        return key;
    }

    static byte[] requireAesCbcIv() {
        byte[] iv = DesensitizeCryptoProviders.getProvider().iv();
        if (iv == null) {
            throw new IllegalStateException("AES iv not found.");
        }
        return iv;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Parsed parse(String encText) {
        if (!encText.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid ENC format: missing prefix");
        }
        int suffixIndex = encText.indexOf(SUFFIX);
        if (suffixIndex < 0) {
            throw new IllegalArgumentException("Invalid ENC format: missing delimiter");
        }
        String header = encText.substring(PREFIX.length(), suffixIndex);
        String payload = encText.substring(suffixIndex + SUFFIX.length());

        String[] parts = header.split(",");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid ENC header: " + header);
        }

        String version = parts[0].trim();
        String kid = null;
        String alg = null;
        String iv = null;
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim();
            int eq = p.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = p.substring(0, eq).trim();
            String v = p.substring(eq + 1).trim();
            if (PARAM_KID.equalsIgnoreCase(k)) {
                kid = v;
            } else if (PARAM_ALG.equalsIgnoreCase(k)) {
                alg = v;
            } else if (PARAM_IV.equalsIgnoreCase(k)) {
                iv = v;
            }
        }
        if (kid == null || kid.isEmpty()) {
            throw new IllegalArgumentException("Invalid ENC header: missing kid");
        }
        if (alg == null || alg.isEmpty()) {
            throw new IllegalArgumentException("Invalid ENC header: missing alg");
        }
        if (payload.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid ENC payload");
        }
        return new Parsed(version, kid, alg, iv, payload);
    }

    private static final class Parsed {
        final String version;
        final String keyId;
        final String alg;
        final String iv;
        final String payloadBase64;

        Parsed(String version, String keyId, String alg, String iv, String payloadBase64) {
            this.version = version;
            this.keyId = keyId;
            this.alg = alg;
            this.iv = iv;
            this.payloadBase64 = payloadBase64;
        }
    }
}
