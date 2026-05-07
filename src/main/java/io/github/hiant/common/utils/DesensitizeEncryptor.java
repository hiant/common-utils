package io.github.hiant.common.utils;

/**
 * Functional interface for pluggable encryption in desensitization rendering.
 * <p>
 * When a field is annotated with {@code @Desensitize(action = DesensitizeAction.ENCRYPT)},
 * the registered encryptor is invoked to transform the raw value into ciphertext.
 * <p>
 * Register via {@link ToStringDesensitizeUtils#setEncryptor(DesensitizeEncryptor)}.
 *
 * @since JDK1.8
 * @see DesensitizeAction#ENCRYPT
 * @see ToStringDesensitizeUtils#setEncryptor(DesensitizeEncryptor)
 */
@FunctionalInterface
public interface DesensitizeEncryptor {

    /**
     * Encrypt the raw sensitive value.
     *
     * @param rawValue
     *            the original plaintext value (never null, never empty)
     * @return the encrypted ciphertext string
     */
    String encrypt(String rawValue);
}
