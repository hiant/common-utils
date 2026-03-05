package io.github.hiant.common.utils;

/**
 * Rendering actions for {@link Desensitize}.
 * <p>
 * This enum describes how a sensitive value should be rendered when generating a safe string representation
 * (e.g. via {@link ToStringDesensitizeUtils}).
 *
 * @since JDK1.8
 */
public enum DesensitizeAction {

    /**
     * Mask the value (default behavior).
     */
    MASK,

    /**
     * Mask the value and append a hash for internal correlation.
     * <p>
     * Note: the hash is not intended for external display.
     */
    MASK_WITH_HASH,

    /**
     * Encrypt the value to a reversible ciphertext for controlled troubleshooting.
     * <p>
     * This requires a key to be provided via {@link DesensitizeCryptoProvider} or system properties.
     */
    ENCRYPT
}
