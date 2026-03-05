package io.github.hiant.common.utils;

import java.nio.charset.StandardCharsets;

/**
 * Crypto helpers for {@link DesensitizeAction#ENCRYPT}.
 * <p>
 * This class defines a small reversible ciphertext format intended for controlled troubleshooting.
 *
 * <h3>Format</h3>
 * {@code ENC[v1,kid=<keyId>,alg=AESGCM]::<base64Payload>}
 * <ul>
 *   <li>{@code v1}: version</li>
 *   <li>{@code kid}: key id</li>
 *   <li>{@code alg}: currently fixed to {@code AESGCM}</li>
 *   <li>{@code base64Payload}: Base64(nonce || ciphertext+tag) as produced by
 *       {@link CipherUtils#encryptWithAESGCM(String, byte[], byte[])}</li>
 * </ul>
 *
 * @since JDK1.8
 */
public final class DesensitizeCryptoUtils {

    private static final String PREFIX     = "ENC[";
    private static final String SUFFIX     = "]::";
    private static final String VERSION_V1 = "v1";
    private static final String ALG_AESGCM = "AESGCM";

    private DesensitizeCryptoUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Encrypt a raw value for safe string rendering.
     *
     * @param rawValue raw value
     * @param keyId    key id (nullable/blank uses provider default)
     * @param aad      additional authenticated data (nullable)
     * @return formatted ciphertext, or null when rawValue is null
     */
    public static String encryptForToString(String rawValue, String keyId, byte[] aad) {
        if (rawValue == null) {
            return null;
        }

        DesensitizeCryptoProvider provider = DesensitizeCryptoProviders.getProvider();
        String effectiveKeyId = (keyId == null || keyId.trim().isEmpty()) ? provider.defaultKeyId() : keyId.trim();
        byte[] key = provider.findAesKey(effectiveKeyId);
        if (key == null) {
            throw new IllegalStateException("AES key not found for keyId: " + effectiveKeyId);
        }

        String payload = CipherUtils.encryptWithAESGCM(rawValue, key, aad);
        if (payload == null) {
            return null;
        }
        return PREFIX + VERSION_V1 + ",kid=" + effectiveKeyId + ",alg=" + ALG_AESGCM + SUFFIX + payload;
    }

    /**
     * Decrypt a formatted ciphertext produced by {@link #encryptForToString(String, String, byte[])}.
     *
     * @param encText formatted ciphertext
     * @param aad     additional authenticated data (nullable)
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
        if (!ALG_AESGCM.equals(parsed.alg)) {
            throw new IllegalArgumentException("Unsupported ENC alg: " + parsed.alg);
        }

        DesensitizeCryptoProvider provider = DesensitizeCryptoProviders.getProvider();
        byte[] key = provider.findAesKey(parsed.keyId);
        if (key == null) {
            throw new IllegalStateException("AES key not found for keyId: " + parsed.keyId);
        }

        return CipherUtils.decryptWithAESGCM(parsed.payloadBase64, key, aad);
    }

    /**
     * Convenience overload without AAD.
     *
     * @param rawValue raw value
     * @param keyId    key id (nullable/blank uses provider default)
     * @return formatted ciphertext, or null when rawValue is null
     */
    public static String encryptForToString(String rawValue, String keyId) {
         return encryptForToString(rawValue, keyId, null);
     }
 
     /**
     * Convenience overload without AAD.
     *
     * @param encText formatted ciphertext
     * @return plaintext, or null when encText is null
     */
    public static String decryptFromToString(String encText) {
         return decryptFromToString(encText, null);
     }
 
     /**
     * Derive a stable AAD for toString encryption from class + field.
     * <p>
     * This is optional but recommended when you want to bind ciphertext to a specific field name.
     *
     * @param declaringClass declaring class
     * @param fieldName      field name
     * @return aad bytes, or null if any argument is null
     */
    public static byte[] toStringAad(Class<?> declaringClass, String fieldName) {
        if (declaringClass == null || fieldName == null) {
            return null;
        }
        return (declaringClass.getName() + "#" + fieldName).getBytes(StandardCharsets.UTF_8);
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
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim();
            int eq = p.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = p.substring(0, eq).trim();
            String v = p.substring(eq + 1).trim();
            if ("kid".equalsIgnoreCase(k)) {
                kid = v;
            } else if ("alg".equalsIgnoreCase(k)) {
                alg = v;
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
        return new Parsed(version, kid, alg, payload);
    }

    private static final class Parsed {
        final String version;
        final String keyId;
        final String alg;
        final String payloadBase64;

        Parsed(String version, String keyId, String alg, String payloadBase64) {
            this.version = version;
            this.keyId = keyId;
            this.alg = alg;
            this.payloadBase64 = payloadBase64;
        }
    }
}
