package io.github.hiant.common.utils;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * Utility class for symmetric and asymmetric encryption/decryption operations.
 * <p>
 * Provides methods for AES (symmetric), DES (symmetric), and RSA (asymmetric) encryption and decryption.
 * </p>
 */
@Slf4j
public class CipherUtils {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private CipherUtils() {
    }

    /**
     * AES encryption mode with CBC and PKCS5 padding.
     */
    private static final String AES_CBC_PKCS5PADDING = "AES/CBC/PKCS5Padding";

    /**
     * DES encryption mode with CBC and PKCS5 padding.
     */
    private static final String DES_CBC_PKCS5PADDING = "DES/CBC/PKCS5Padding";

    /**
     * AES algorithm name.
     */
    private static final String AES_ALGORITHM = "AES";

    /**
     * DES algorithm name.
     */
    private static final String DES_ALGORITHM = "DES";

    /**
     * RSA algorithm name.
     */
    private static final String RSA_ALGORITHM = "RSA";

    /**
     * Encrypts the given content using AES algorithm with CBC mode and PKCS5 padding.
     *
     * @param content   The plaintext content to encrypt.
     * @param secretKey The secret key (will be padded/truncated to 16 bytes).
     * @param iv        The initialization vector (IV), must be 16 bytes.
     * @return Base64-encoded encrypted string, or null if encryption fails.
     */
    public static String encryptWithAES(String content, String secretKey, String iv) {
        SecretKeySpec key = new SecretKeySpec(padTo16Bytes(secretKey), AES_ALGORITHM);
        IvParameterSpec params = new IvParameterSpec(padTo16Bytes(iv));

        return encrypt(content, AES_CBC_PKCS5PADDING, key, params);
    }

    /**
     * Decrypts the given content using AES algorithm with CBC mode and PKCS5 padding.
     *
     * @param content   The Base64-encoded encrypted string.
     * @param secretKey The secret key (must match the one used for encryption).
     * @param iv        The initialization vector (IV) used during encryption.
     * @return Decrypted plaintext string, or null if decryption fails.
     */
    public static String decryptWithAES(String content, String secretKey, String iv) {
        SecretKeySpec key = new SecretKeySpec(padTo16Bytes(secretKey), AES_ALGORITHM);
        IvParameterSpec params = new IvParameterSpec(padTo16Bytes(iv));

        return decrypt(content, AES_CBC_PKCS5PADDING, key, params);
    }

    /**
     * Encrypts the given content using DES algorithm with CBC mode and PKCS5 padding.
     *
     * @param content   The plaintext content to encrypt.
     * @param secretKey The secret key (will be padded/truncated to 16 bytes).
     * @param iv        The initialization vector (IV), must be 16 bytes.
     * @return Base64-encoded encrypted string, or null if encryption fails.
     */
    public static String encryptWithDES(String content, String secretKey, String iv) {
        SecretKeySpec key = new SecretKeySpec(padTo16Bytes(secretKey), DES_ALGORITHM);
        IvParameterSpec params = new IvParameterSpec(padTo16Bytes(iv));

        return encrypt(content, DES_CBC_PKCS5PADDING, key, params);
    }

    /**
     * Decrypts the given content using DES algorithm with CBC mode and PKCS5 padding.
     *
     * @param content   The Base64-encoded encrypted string.
     * @param secretKey The secret key (must match the one used for encryption).
     * @param iv        The initialization vector (IV) used during encryption.
     * @return Decrypted plaintext string, or null if decryption fails.
     */
    public static String decryptWithDES(String content, String secretKey, String iv) {
        SecretKeySpec key = new SecretKeySpec(padTo16Bytes(secretKey), DES_ALGORITHM);
        IvParameterSpec params = new IvParameterSpec(padTo16Bytes(iv));

        return decrypt(content, DES_CBC_PKCS5PADDING, key, params);
    }

    /**
     * Encrypts the given content using RSA algorithm.
     *
     * @param content The plaintext content to encrypt.
     * @param key     The RSA public/private key to use for encryption.
     * @return Base64-encoded encrypted string, or null if encryption fails.
     */
    public static String encryptWithRSA(String content, Key key) {
        return encrypt(content, RSA_ALGORITHM, key, null);
    }

    /**
     * Decrypts the given content using RSA algorithm.
     *
     * @param content The Base64-encoded encrypted string.
     * @param key     The RSA private/public key to use for decryption.
     * @return Decrypted plaintext string, or null if decryption fails.
     */
    public static String decryptWithRSA(String content, Key key) {
        return decrypt(content, RSA_ALGORITHM, key, null);
    }

    /**
     * Generic encryption method supporting various cipher algorithms.
     *
     * @param content    The plaintext content to encrypt.
     * @param cipherMode The cipher transformation (e.g., AES/CBC/PKCS5Padding).
     * @param key        The cryptographic key.
     * @param params     Optional algorithm parameters (e.g., IV for AES).
     * @return Base64-encoded encrypted string, or null if encryption fails.
     */
    public static String encrypt(String content, String cipherMode, Key key, AlgorithmParameterSpec params) {
        return encrypt(content, cipherMode, key, params, e -> log.error("Encryption failed: ", e));
    }

    /**
     * Generic encryption method supporting various cipher algorithms.
     *
     * @param content    The plaintext content to encrypt.
     * @param cipherMode The cipher transformation (e.g., AES/CBC/PKCS5Padding).
     * @param key        The cryptographic key.
     * @param params     Optional algorithm parameters (e.g., IV for AES).
     * @param consumer   Consumer to handle exceptions during encryption.
     * @return Base64-encoded encrypted string, or null if encryption fails.
     */
    public static String encrypt(String content, String cipherMode, Key key, AlgorithmParameterSpec params, Consumer<Throwable> consumer) {
        try {
            Cipher cipher = Cipher.getInstance(cipherMode);
            if (params == null) {
                cipher.init(Cipher.ENCRYPT_MODE, key);
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, key, params);
            }

            byte[] encryptBytes = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return new String(Base64.getEncoder().encode(encryptBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (consumer != null) {
                consumer.accept(e);
            }
        }
        return null;
    }

    /**
     * Generic decryption method supporting various cipher algorithms.
     *
     * @param content    The Base64-encoded encrypted string.
     * @param cipherMode The cipher transformation (e.g., AES/CBC/PKCS5Padding).
     * @param key        The cryptographic key.
     * @param params     Optional algorithm parameters (e.g., IV for AES).
     * @return Decrypted plaintext string, or null if decryption fails.
     */
    public static String decrypt(String content, String cipherMode, Key key, AlgorithmParameterSpec params) {
        return decrypt(content, cipherMode, key, params, e -> log.error("Decryption failed: ", e));
    }

    /**
     * Generic decryption method supporting various cipher algorithms.
     *
     * @param content    The Base64-encoded encrypted string.
     * @param cipherMode The cipher transformation (e.g., AES/CBC/PKCS5Padding).
     * @param key        The cryptographic key.
     * @param params     Optional algorithm parameters (e.g., IV for AES).
     * @param consumer   Consumer to handle exceptions during decryption.
     * @return Decrypted plaintext string, or null if decryption fails.
     */
    public static String decrypt(String content, String cipherMode, Key key, AlgorithmParameterSpec params, Consumer<Throwable> consumer) {
        try {
            Cipher cipher = Cipher.getInstance(cipherMode);
            if (params == null) {
                cipher.init(Cipher.DECRYPT_MODE, key);
            } else {
                cipher.init(Cipher.DECRYPT_MODE, key, params);
            }

            byte[] data = cipher.doFinal(Base64.getDecoder().decode(content));
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (consumer != null) {
                consumer.accept(e);
            }
        }
        return null;
    }

    /**
     * Converts a string into a fixed-length 16-byte array by padding or truncating.
     * <p>
     * Used primarily for generating 16-byte keys and IVs from arbitrary strings.
     * If input is shorter than 16 characters, it's padded with zeros.
     * If longer, it's truncated to 16 characters.
     * </p>
     *
     * @param str The input string.
     * @return A 16-byte array representation of the string.
     */
    private static byte[] padTo16Bytes(String str) {
        if (str == null) {
            str = "";
        }

        int maxLen = 16;
        StringBuilder sb = new StringBuilder(maxLen);
        sb.append(str);
        while (sb.length() < maxLen) {
            sb.append("0");
        }
        if (sb.length() > maxLen) {
            sb.setLength(maxLen);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generates an RSA key pair with a key size of 2048 bits.
     *
     * @return a {@link KeyPair} containing the generated public and private keys
     * @throws NoSuchAlgorithmException if the RSA algorithm is not available in the current environment
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Saves the public and private keys of an RSA key pair to the specified file paths in Base64-encoded format.
     *
     * @param publicKeyPath  the file path where the public key will be saved
     * @param privateKeyPath the file path where the private key will be saved
     * @param keyPair        the {@link KeyPair} to save
     * @throws IOException if an I/O error occurs during file writing
     */
    public static void saveKeys(String publicKeyPath, String privateKeyPath, KeyPair keyPair) throws IOException {
        PublicKey publicKey = keyPair.getPublic();
        byte[] publicKeyBytes = publicKey.getEncoded();
        String publicKeyString = Base64.getEncoder().encodeToString(publicKeyBytes);
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(publicKeyPath))) {
            writer.write(publicKeyString);
        }

        PrivateKey privateKey = keyPair.getPrivate();
        byte[] privateKeyBytes = privateKey.getEncoded();
        String privateKeyString = Base64.getEncoder().encodeToString(privateKeyBytes);
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(privateKeyPath))) {
            writer.write(privateKeyString);
        }
    }

    /**
     * Loads a public key from the specified file.
     * <p>
     * The file should contain a Base64-encoded X.509 encoded public key.
     *
     * @param keyPath the file path from which to load the public key
     * @return the loaded {@link PublicKey}
     * @throws Exception if an error occurs during reading or key specification parsing
     */
    public static PublicKey loadPublicKey(String keyPath) throws Exception {
        String publicKeyString = new String(Files.readAllBytes(Paths.get(keyPath)), StandardCharsets.UTF_8);
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyString);

        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Loads a private key from the specified file.
     * <p>
     * The file should contain a Base64-encoded PKCS#8 encoded private key.
     *
     * @param keyPath the file path from which to load the private key
     * @return the loaded {@link PrivateKey}
     * @throws Exception if an error occurs during reading or key specification parsing
     */
    public static PrivateKey loadPrivateKey(String keyPath) throws Exception {
        String privateKeyString = new String(Files.readAllBytes(Paths.get(keyPath)), StandardCharsets.UTF_8);
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyString);

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
        return keyFactory.generatePrivate(keySpec);
    }


}
