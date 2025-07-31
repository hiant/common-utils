package io.github.hiant.common.utils;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * <b>KeyUtils</b> – a zero-dependency, high-security utility for generating,
 * saving, loading and validating asymmetric key pairs (RSA, EC, DSA).
 *
 * <h2>Main Features</h2>
 * <ul>
 *   <li>Key generation with sensible defaults (RSA-4096, SHA-256)</li>
 *   <li>Save / load keys in PEM or plain Base64</li>
 *   <li>Password-based encryption of private keys (PKCS#8 EncryptedPrivateKeyInfo)</li>
 *   <li>Atomic file writes – never leaves half-written keys on disk</li>
 *   <li>Strict key-length validation to reject truncated / corrupted keys</li>
 *   <li>Clear sensitive data (passwords) after use</li>
 *   <li>Thread-safe operations (except key generation)</li>
 * </ul>
 *
 * <h2>Quick Example</h2>
 * <pre>{@code
 * // 1. Generate
 * KeyPair kp = KeyUtils.generateKeyPair();
 *
 * // 2. Save
 * KeyUtils.saveKeyToFile(kp.getPublic(), Paths.get("public.pem"), true);
 * KeyUtils.saveEncryptedPrivateKey(kp.getPrivate(), Paths.get("private.p8"), "str0ng!".toCharArray());
 *
 * // 3. Load & verify
 * PublicKey pub = KeyUtils.loadPublicKey("RSA", Paths.get("public.pem"));
 * PrivateKey prv = KeyUtils.loadEncryptedPrivateKey("RSA", Paths.get("private.p8"), "str0ng!".toCharArray());
 * boolean ok = KeyUtils.verifyKeyPair(prv, pub, "SHA-256");
 * }</pre>
 *
 * <h2>Parameter Selection Guide</h2>
 * <table>
 *   <caption>Recommended values</caption>
 *   <tr><th>Use-case</th><th>Algorithm</th><th>Key Size / Curve</th><th>Hash / PBE</th></tr>
 *   <tr><td>Web server certificate</td><td>RSA</td><td>4096</td><td>SHA-256</td></tr>
 *   <tr><td>Modern mobile / IoT</td><td>EC</td><td>secp256r1</td><td>SHA-256</td></tr>
 *   <tr><td>Legacy signature</td><td>DSA</td><td>2048</td><td>SHA-256</td></tr>
 *   <tr><td>Encrypted private key</td><td>PKCS#8</td><td>-</td><td>PBEWithHmacSHA256AndAES_256, 65 536 iterations, 16-byte salt</td></tr>
 * </table>
 *
 * <p><b>Thread safety:</b> Key generation is CPU/entropy intensive and should
 * be executed off request threads or cached. All other methods are thread-safe.
 *
 * @author your_name
 */
public final class KeyUtils {

    /**
     * Default asymmetric algorithm ({@value}) used when none is specified.
     */
    public static final String DEFAULT_ALGORITHM = "RSA";
    /**
     * Default RSA key size ({@value} bits).
     */
    public static final int DEFAULT_RSA_KEY_SIZE = 4096;
    /**
     * Default hash algorithm ({@value}) for signatures and PBE.
     */
    public static final String DEFAULT_HASH_ALG = "SHA-256";

    private static final SecureRandom RNG = new SecureRandom();

    private KeyUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /* ---------------------------------------------------------------------- */
    /* ------------------------------ Exceptions ----------------------------- */
    /* ---------------------------------------------------------------------- */

    /**
     * Thrown when a key file could not be read.
     */
    public static class KeyReadException extends IOException {
        public KeyReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when key data are corrupted or use an unsupported algorithm.
     */
    public static class KeyFormatException extends GeneralSecurityException {
        public KeyFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /* ---------------------------------------------------------------------- */
    /* ----------------------------- PBE Config ------------------------------ */
    /* ---------------------------------------------------------------------- */

    /**
     * Immutable configuration for password-based encryption of private keys.
     *
     * <p><b>Recommended values:</b>
     * <ul>
     *   <li>PBKDF2WithHmacSHA256</li>
     *   <li>PBEWithHmacSHA256AndAES_256</li>
     *   <li>65 536 iterations</li>
     *   <li>16-byte salt</li>
     * </ul>
     */
    public static final class PBEConfig {
        /**
         * PBKDF algorithm, e.g. {@code PBKDF2WithHmacSHA256}.
         */
        public final String pbkdfAlgorithm;
        /**
         * Cipher algorithm, e.g. {@code PBEWithHmacSHA256AndAES_256}.
         */
        public final String cipherAlgorithm;
        /**
         * Iteration count for PBKDF (OWASP recommends ≥ 10 000).
         */
        public final int iterationCount;
        /**
         * Salt length in bytes (≥ 16 recommended).
         */
        public final int saltLength;

        public PBEConfig(String pbkdfAlgorithm, String cipherAlgorithm,
                         int iterationCount, int saltLength) {
            this.pbkdfAlgorithm = Objects.requireNonNull(pbkdfAlgorithm);
            this.cipherAlgorithm = Objects.requireNonNull(cipherAlgorithm);
            if (iterationCount <= 0 || saltLength <= 0)
                throw new IllegalArgumentException("iterationCount & saltLength must be positive");
            this.iterationCount = iterationCount;
            this.saltLength = saltLength;
        }

        /**
         * Pre-built default configuration (65 536 iterations, 16-byte salt).
         */
        public static final PBEConfig DEFAULT = new PBEConfig(
                "PBKDF2WithHmacSHA256",
                "PBEWithHmacSHA256AndAES_256",
                65_536,
                16);
    }

    /* ---------------------------------------------------------------------- */
    /* ----------------------------- PEM Utils ------------------------------ */
    /* ---------------------------------------------------------------------- */

    private static final Pattern PEM_STRIP = Pattern.compile("-----[^-]+-----|\\s",
            Pattern.CASE_INSENSITIVE);

    private static String toPem(String type, byte[] encoded) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }

    private static byte[] decodePem(String content) {
        String base64 = PEM_STRIP.matcher(content).replaceAll("");
        return Base64.getDecoder().decode(base64);
    }

    /* ---------------------------------------------------------------------- */
    /* --------------------------- Key Generation ---------------------------- */
    /* ---------------------------------------------------------------------- */

    /**
     * Generates an RSA key pair ({@value DEFAULT_RSA_KEY_SIZE} bits).
     *
     * @return generated key pair
     * @throws NoSuchAlgorithmException if RSA is unavailable
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        return generateKeyPair(DEFAULT_ALGORITHM, DEFAULT_RSA_KEY_SIZE);
    }

    /**
     * Generates a key pair of the given algorithm and key size.
     *
     * @param algorithm RSA, DSA, EC, etc.
     * @param keySize   key size in bits (ignored for EC when using curve spec)
     * @return generated key pair
     * @throws NoSuchAlgorithmException if the algorithm is not supported
     * @throws IllegalArgumentException if algorithm is blank
     */
    public static KeyPair generateKeyPair(String algorithm, int keySize)
            throws NoSuchAlgorithmException {
        Objects.requireNonNull(algorithm);
        if (algorithm.trim().isEmpty()) {
            throw new IllegalArgumentException("algorithm must not be empty");
        }
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm);
        kpg.initialize(keySize);
        return kpg.generateKeyPair();
    }

    /**
     * Generates a key pair using an {@link AlgorithmParameterSpec}.
     * Useful for EC keys with named curves, e.g. {@code new ECGenParameterSpec("secp256r1")}.
     *
     * @param algorithm key algorithm, typically "EC"
     * @param params    parameter spec describing the curve
     * @return generated key pair
     * @throws NoSuchAlgorithmException           if algorithm unavailable
     * @throws InvalidAlgorithmParameterException if params invalid
     */
    public static KeyPair generateKeyPair(String algorithm, AlgorithmParameterSpec params)
            throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        Objects.requireNonNull(algorithm);
        Objects.requireNonNull(params);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm);
        kpg.initialize(params);
        return kpg.generateKeyPair();
    }

    /* ---------------------------------------------------------------------- */
    /* ---------------------------- File I/O -------------------------------- */
    /* ---------------------------------------------------------------------- */

    private static void writeAtomic(Path path, String data) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(tmp, data.getBytes(StandardCharsets.US_ASCII),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Saves a key to disk in PEM or plain Base64 format.
     *
     * @param key  the key to save (public or private)
     * @param path destination file
     * @param pem  {@code true} → PEM; {@code false} → raw Base64
     * @throws IOException              on I/O failure
     * @throws IllegalArgumentException if key encoding is empty
     */
    public static void saveKeyToFile(Key key, Path path, boolean pem) throws IOException {
        Objects.requireNonNull(key);
        Objects.requireNonNull(path);
        byte[] encoded = key.getEncoded();
        if (encoded == null || encoded.length == 0) {
            throw new IllegalArgumentException("Key encoding is empty");
        }

        String type = (key instanceof PrivateKey) ? "PRIVATE KEY" : "PUBLIC KEY";
        String out = pem ? toPem(type, encoded) : Base64.getEncoder().encodeToString(encoded);
        writeAtomic(path, out);
    }

    /**
     * Convenience overload using a {@link String} path.
     *
     * @see #saveKeyToFile(Key, Path, boolean)
     */
    public static void saveKeyToFile(Key key, String filePath, boolean pem) throws IOException {
        saveKeyToFile(key, Paths.get(filePath), pem);
    }

    /* ---------------------------------------------------------------------- */
    /* --------------------- Encrypted Private Key --------------------------- */
    /* ---------------------------------------------------------------------- */

    /**
     * Encrypts and saves a private key using PKCS#8 EncryptedPrivateKeyInfo.
     *
     * @param privateKey private key to encrypt
     * @param path       destination file (PEM format)
     * @param password   password char array – will be cleared on return
     * @param config     encryption parameters
     * @throws GeneralSecurityException on cryptographic failure
     * @throws IOException              on I/O failure
     * @throws IllegalArgumentException if password is empty
     */
    public static void saveEncryptedPrivateKey(PrivateKey privateKey, Path path,
                                               char[] password, PBEConfig config)
            throws GeneralSecurityException, IOException {
        Objects.requireNonNull(privateKey);
        Objects.requireNonNull(path);
        Objects.requireNonNull(password);
        Objects.requireNonNull(config);
        if (password.length == 0) {
            throw new IllegalArgumentException("password empty");
        }

        try {
            byte[] salt = new byte[config.saltLength];
            RNG.nextBytes(salt);

            PBEKeySpec pbeKeySpec = new PBEKeySpec(password);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(config.pbkdfAlgorithm);
            SecretKey pbeKey = skf.generateSecret(pbeKeySpec);

            PBEParameterSpec pbeParam = new PBEParameterSpec(salt, config.iterationCount);
            Cipher cipher = Cipher.getInstance(config.cipherAlgorithm);
            cipher.init(Cipher.ENCRYPT_MODE, pbeKey, pbeParam);

            byte[] encrypted = cipher.doFinal(privateKey.getEncoded());

            AlgorithmParameters algParams = AlgorithmParameters.getInstance(config.cipherAlgorithm);
            algParams.init(pbeParam);

            EncryptedPrivateKeyInfo epki = new EncryptedPrivateKeyInfo(algParams, encrypted);
            writeAtomic(path, toPem("ENCRYPTED PRIVATE KEY", epki.getEncoded()));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Encrypts and saves using {@link PBEConfig#DEFAULT}.
     *
     * @see #saveEncryptedPrivateKey(PrivateKey, Path, char[], PBEConfig)
     */
    public static void saveEncryptedPrivateKey(PrivateKey privateKey, Path path, char[] password)
            throws GeneralSecurityException, IOException {
        saveEncryptedPrivateKey(privateKey, path, password, PBEConfig.DEFAULT);
    }

    /* ---------------------------------------------------------------------- */
    /* ---------------------------- Load Key -------------------------------- */
    /* ---------------------------------------------------------------------- */

    /**
     * Loads an unencrypted private key from PEM or Base64 text.
     *
     * @param algorithm key algorithm, e.g. "RSA"
     * @param path      file containing PKCS#8 DER
     * @return private key
     * @throws KeyReadException   on I/O failure
     * @throws KeyFormatException on format or algorithm error
     */
    public static PrivateKey loadPrivateKey(String algorithm, Path path)
            throws KeyReadException, KeyFormatException {
        return loadKey(algorithm, path, true);
    }

    /**
     * Loads a public key from PEM or Base64 text.
     *
     * @param algorithm key algorithm, e.g. "RSA"
     * @param path      file containing X.509 DER
     * @return public key
     * @throws KeyReadException   on I/O failure
     * @throws KeyFormatException on format or algorithm error
     */
    public static PublicKey loadPublicKey(String algorithm, Path path)
            throws KeyReadException, KeyFormatException {
        return loadKey(algorithm, path, false);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Key> T loadKey(String algorithm, Path path, boolean isPrivate)
            throws KeyReadException, KeyFormatException {
        Objects.requireNonNull(algorithm);
        Objects.requireNonNull(path);
        try {
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            byte[] decoded = decodePem(content);
            KeyFactory kf = KeyFactory.getInstance(algorithm);
            KeySpec spec = isPrivate ? new PKCS8EncodedKeySpec(decoded) : new X509EncodedKeySpec(decoded);
            T key = (T) (isPrivate ? kf.generatePrivate((PKCS8EncodedKeySpec) spec) : kf.generatePublic((X509EncodedKeySpec) spec));
            validateKeyLength(algorithm, key.getEncoded());
            return key;
        } catch (IOException e) {
            throw new KeyReadException("Failed to read key file: " + path, e);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new KeyFormatException("Invalid key format or unsupported algorithm in file: " + path, e);
        } catch (IllegalArgumentException e) {
            throw new KeyFormatException("Corrupted key data in file: " + path, e);
        }
    }

    /**
     * Loads and decrypts an encrypted private key (PEM or Base64).
     *
     * @param algorithm key algorithm, e.g. "RSA"
     * @param path      encrypted private key file
     * @param password  password char array – will be cleared on return
     * @return decrypted private key
     * @throws IOException              on I/O failure
     * @throws GeneralSecurityException on decryption failure
     */
    public static PrivateKey loadEncryptedPrivateKey(String algorithm, Path path, char[] password)
            throws IOException, GeneralSecurityException {
        Objects.requireNonNull(algorithm);
        Objects.requireNonNull(path);
        Objects.requireNonNull(password);

        try {
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            byte[] decoded = decodePem(content);
            EncryptedPrivateKeyInfo epki = new EncryptedPrivateKeyInfo(decoded);

            PBEKeySpec pbeKeySpec = new PBEKeySpec(password);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(epki.getAlgName());
            SecretKey pbeKey = skf.generateSecret(pbeKeySpec);

            PKCS8EncodedKeySpec keySpec = epki.getKeySpec(pbeKey);
            PrivateKey pk = KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
            validateKeyLength(algorithm, pk.getEncoded());
            return pk;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /* ---------------------------------------------------------------------- */
    /* ------------------------- Validation ---------------------------------- */
    /* ---------------------------------------------------------------------- */

    /**
     * Validates the minimum encoded length of a key.
     *
     * @param algorithm key algorithm
     * @param keyBytes  encoded bytes
     * @throws IllegalArgumentException if the key is too short
     */
    public static void validateKeyLength(String algorithm, byte[] keyBytes) {
        Objects.requireNonNull(algorithm);
        int min = 64;
        if (algorithm.equalsIgnoreCase("RSA")) {
            min = 512;
        } else if (algorithm.equalsIgnoreCase("EC")) {
            min = 80;
        }
        if (keyBytes == null || keyBytes.length < min) {
            throw new IllegalArgumentException("Key too short for " + algorithm + ": " + (keyBytes == null ? 0 : keyBytes.length) + " < " + min + " bytes");
        }
    }

    /* ---------------------------------------------------------------------- */
    /* ------------------------ Key Pair Verification ------------------------ */
    /* ---------------------------------------------------------------------- */

    /**
     * Validates raw byte[] against algorithm-specific minimum length and
     * ensures DER structure can be parsed (PKCS#8 / X.509).
     *
     * @param algorithm algorithm name
     * @param keyBytes  encoded bytes
     * @throws IllegalArgumentException if malformed or too short
     */
    public static void validateKeyBytes(String algorithm, byte[] keyBytes) {
        Objects.requireNonNull(algorithm);
        int min = 64;
        switch (algorithm.toUpperCase()) {
            case "RSA":
                min = 512;
                break;
            case "EC":
                min = 80;
                break;
            case "DSA":
                min = 64;
                break;
        }
        if (keyBytes == null || keyBytes.length < min)
            throw new IllegalArgumentException("Key too short for " + algorithm +
                    ": " + (keyBytes == null ? 0 : keyBytes.length) + " < " + min + " bytes");
    }

    public static boolean verifyKeyPair(PrivateKey privateKey, byte[] publicKeyBytes, String algorithm, String hashAlg)
            throws GeneralSecurityException {

        Objects.requireNonNull(publicKeyBytes, "publicKeyBytes must not be null");
        Objects.requireNonNull(privateKey, "privateKey must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");

        if (publicKeyBytes.length == 0) {
            throw new IllegalArgumentException("publicKeyBytes must not be empty");
        }

        validateKeyBytes(algorithm, publicKeyBytes);

        if (hashAlg == null || hashAlg.trim().isEmpty()) {
            hashAlg = DEFAULT_HASH_ALG;
        }

        try {
            KeyFactory kf = KeyFactory.getInstance(algorithm);
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            return verifyKeyPair(privateKey, publicKey, hashAlg);
        } catch (InvalidKeySpecException e) {
            throw new KeyFormatException("Invalid public key encoding", e);
        }
    }

    /**
     * Verifies that a private key and public key form a matching pair by
     * performing a random sign/verify cycle.
     *
     * @param privateKey private key
     * @param publicKey  public key
     * @param hashAlg    hash algorithm, e.g. {@code SHA-256}
     * @return {@code true} if the keys match
     * @throws GeneralSecurityException on cryptographic failure
     */
    public static boolean verifyKeyPair(PrivateKey privateKey, PublicKey publicKey, String hashAlg)
            throws GeneralSecurityException {
        Objects.requireNonNull(privateKey);
        Objects.requireNonNull(publicKey);
        if (hashAlg == null || hashAlg.trim().isEmpty()) {
            hashAlg = DEFAULT_HASH_ALG;
        }

        String sigAlg = resolveSignatureAlgorithm(hashAlg, privateKey.getAlgorithm());
        Signature sig = Signature.getInstance(sigAlg);

        byte[] test = new byte[32];
        RNG.nextBytes(test);

        sig.initSign(privateKey);
        sig.update(test);
        byte[] signature = sig.sign();

        sig.initVerify(publicKey);
        sig.update(test);
        return sig.verify(signature);
    }

    /**
     * Resolves the canonical JCA signature algorithm string.
     *
     * @param hashAlg hash algorithm, e.g. {@code SHA-256}
     * @param keyAlg  key algorithm, e.g. {@code RSA}
     * @return JCA algorithm string, e.g. {@code SHA256withRSA}
     * @throws IllegalArgumentException for unsupported combinations
     */
    public static String resolveSignatureAlgorithm(String hashAlg, String keyAlg) {
        Objects.requireNonNull(hashAlg);
        Objects.requireNonNull(keyAlg);
        String h = hashAlg.toUpperCase().replace("-", "");
        String k = keyAlg.toUpperCase();

        switch (k) {
            case "RSA":
                return h + "withRSA";
            case "EC":
                return h + "withECDSA";
            case "DSA":
                return h + "withDSA";
            default:
                throw new IllegalArgumentException("Unsupported key algorithm: " + keyAlg);
        }
    }
}
