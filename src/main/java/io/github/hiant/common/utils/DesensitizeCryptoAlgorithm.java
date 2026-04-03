package io.github.hiant.common.utils;

/**
 * Encryption algorithms supported by {@link DesensitizeAction#ENCRYPT}.
 *
 * @since JDK1.8
 */
public enum DesensitizeCryptoAlgorithm {

    /**
     * AES-GCM with optional field-bound AAD.
     */
    AES_GCM,

    /**
     * AES-CBC compatibility mode backed by {@link CipherUtils#encryptWithAES(String, String, String)}.
     */
    AES_CBC
}
