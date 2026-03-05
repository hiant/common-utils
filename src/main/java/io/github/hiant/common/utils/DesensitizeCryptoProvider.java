package io.github.hiant.common.utils;

/**
 * Provider abstraction for desensitization encryption keys.
 * <p>
 * This project is dependency-free and does not assume any DI container. Host applications (e.g. Spring)
 * can implement this interface and register it via {@link DesensitizeCryptoProviders#setProvider(DesensitizeCryptoProvider)}
 * to load keys from a configuration center or KMS.
 * <p>
 * The default implementation is system-property based.
 *
 * @since JDK1.8
 */
public interface DesensitizeCryptoProvider {

    /**
     * Returns the default key id used when an annotation does not specify a key id.
     *
     * @return default key id (non-null)
     */
    String defaultKeyId();

    /**
     * Find the raw AES key bytes by key id.
     * <p>
     * Supported sizes: 16/24/32 bytes (AES-128/192/256).
     *
     * @param keyId key id
     * @return key bytes, or null when not found
     */
    byte[] findAesKey(String keyId);
}
