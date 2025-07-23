package io.github.hiant.common.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Utility class for computing and verifying message digests such as MD5, SHA-256, etc.
 * Supports hashing of strings, byte arrays, files, batch sidecar digest files,
 * digest verification, algorithm aliasing, and configurable hexadecimal output case.
 */
public final class DigestUtils {

    private static final int BUFFER_SIZE = 8192;
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * Supported canonical algorithm names.
     */
    private static final Set<String> SUPPORTED_ALGORITHMS;

    /**
     * Algorithm alias map for input normalization.
     */
    private static final Map<String, String> ALGORITHM_ALIASES;

    static {
        Set<String> algos = new HashSet<>();
        algos.add("MD5");
        algos.add("SHA-1");
        algos.add("SHA-256");
        algos.add("SHA-512");
        SUPPORTED_ALGORITHMS = Collections.unmodifiableSet(algos);

        Map<String, String> aliases = new HashMap<>();
        aliases.put("MD5", "MD5");
        aliases.put("md5", "MD5");

        aliases.put("SHA1", "SHA-1");
        aliases.put("sha1", "SHA-1");
        aliases.put("SHA-1", "SHA-1");

        aliases.put("SHA256", "SHA-256");
        aliases.put("sha256", "SHA-256");
        aliases.put("SHA-256", "SHA-256");

        aliases.put("SHA512", "SHA-512");
        aliases.put("sha512", "SHA-512");
        aliases.put("SHA-512", "SHA-512");

        ALGORITHM_ALIASES = Collections.unmodifiableMap(aliases);
    }

    private DigestUtils() {
        throw new AssertionError("Utility class should not be instantiated.");
    }

    /**
     * Returns all supported canonical hash algorithm names.
     *
     * @return Unmodifiable set of supported algorithm names.
     */
    public static Set<String> getSupportedAlgorithms() {
        return SUPPORTED_ALGORITHMS;
    }

    /**
     * Normalizes algorithm name using alias map.
     *
     * @param input Algorithm name input (case-insensitive, may be alias).
     * @return Normalized canonical algorithm name.
     * @throws IllegalArgumentException If input is null.
     */
    private static String normalizeAlgorithm(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Algorithm cannot be null");
        }
        String normalized = ALGORITHM_ALIASES.get(input);
        if (normalized == null) {
            normalized = ALGORITHM_ALIASES.get(input.toUpperCase());
        }
        if (normalized == null) {
            normalized = input;
        }
        return normalized;
    }

    /**
     * Validates whether the algorithm is supported.
     *
     * @param algorithm Algorithm name (alias or canonical).
     * @throws IllegalArgumentException if algorithm is not supported.
     */
    private static void validateAlgorithm(String algorithm) {
        String normalized = normalizeAlgorithm(algorithm);
        if (!SUPPORTED_ALGORITHMS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }
    }

    /**
     * Computes the digest of the given text (UTF-8 encoded) using the specified algorithm.
     *
     * @param text      Input text.
     * @param algorithm Hash algorithm name or alias.
     * @return Hexadecimal string (lowercase) of the digest, or null if text is null.
     * @throws IllegalArgumentException if algorithm is not supported.
     */
    public static String digest(String text, String algorithm) {
        if (text == null) return null;
        return digest(text.getBytes(StandardCharsets.UTF_8), algorithm);
    }

    /**
     * Computes the digest of the given byte array using the specified algorithm.
     *
     * @param input     Input byte array.
     * @param algorithm Hash algorithm name or alias.
     * @return Hexadecimal string (lowercase) of the digest, or null if input is null.
     * @throws IllegalArgumentException if algorithm is not supported.
     */
    public static String digest(byte[] input, String algorithm) {
        byte[] raw = digestRaw(input, algorithm);
        return toHex(raw, false);
    }

    /**
     * Computes the digest of the contents of the specified file using the given algorithm.
     *
     * @param file      Path to the file to hash.
     * @param algorithm Hash algorithm name or alias.
     * @return Hexadecimal string (lowercase) of the digest, or null if file is not readable.
     * @throws IOException              If file reading fails.
     * @throws IllegalArgumentException If algorithm is not supported.
     */
    public static String digest(Path file, String algorithm) throws IOException {
        byte[] raw = digestRaw(file, algorithm);
        return toHex(raw, false);
    }

    /**
     * Computes the raw digest bytes of the given byte array.
     *
     * @param input     Input byte array.
     * @param algorithm Hash algorithm name or alias.
     * @return Raw digest bytes.
     * @throws IllegalArgumentException if algorithm is not supported.
     */
    public static byte[] digestRaw(byte[] input, String algorithm) {
        validateAlgorithm(algorithm);
        String algo = normalizeAlgorithm(algorithm);
        try {
            MessageDigest md = MessageDigest.getInstance(algo);
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm, e);
        }
    }

    /**
     * Computes the raw digest bytes of the contents of the specified file.
     *
     * @param file      Path to the file to hash.
     * @param algorithm Hash algorithm name or alias.
     * @return Raw digest bytes, or null if file is not readable.
     * @throws IOException              If file reading fails.
     * @throws IllegalArgumentException If algorithm is not supported.
     */
    public static byte[] digestRaw(Path file, String algorithm) throws IOException {
        validateAlgorithm(algorithm);
        String algo = normalizeAlgorithm(algorithm);
        Objects.requireNonNull(file);
        if (!Files.isReadable(file)) return null;

        try (InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
            MessageDigest md = MessageDigest.getInstance(algo);
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = is.read(buffer)) != -1) {
                md.update(buffer, 0, len);
            }
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm, e);
        }
    }

    /**
     * Verifies if the digest of the given text matches the expected digest.
     *
     * @param text           Input text.
     * @param expectedDigest Expected hex digest string (case-insensitive).
     * @param algorithm      Hash algorithm name or alias.
     * @return True if digests match; false otherwise or if expectedDigest is null.
     * @throws IllegalArgumentException if algorithm is not supported.
     */
    public static boolean verifyText(String text, String expectedDigest, String algorithm) {
        if (expectedDigest == null) return false;
        String actual = digest(text, algorithm);
        return expectedDigest.equalsIgnoreCase(actual);
    }

    /**
     * Verifies if the digest of the given file matches the expected digest.
     *
     * @param file           Path to the file.
     * @param expectedDigest Expected hex digest string (case-insensitive).
     * @param algorithm      Hash algorithm name or alias.
     * @return True if digests match; false otherwise or if expectedDigest is null.
     * @throws IOException              If file reading fails.
     * @throws IllegalArgumentException If algorithm is not supported.
     */
    public static boolean verifyFile(Path file, String expectedDigest, String algorithm) throws IOException {
        if (expectedDigest == null) return false;
        String actual = digest(file, algorithm);
        return expectedDigest.equalsIgnoreCase(actual);
    }

    /**
     * Writes a digest sidecar file for one file.
     * Sidecar filename is constructed by appending ".md5sum", ".sha256sum", etc. to original filename.
     * Format: &lt;digest&gt;  &lt;filename&gt;
     *
     * @param file      File to hash.
     * @param algorithm Hash algorithm name or alias.
     * @return Path to the created sidecar file.
     * @throws IOException              If writing fails.
     * @throws IllegalArgumentException If algorithm is not supported.
     */
    public static Path writeDigestSidecar(Path file, String algorithm) throws IOException {
        validateAlgorithm(algorithm);
        Objects.requireNonNull(file);
        String digest = digest(file, algorithm);
        if (digest == null) return null;

        String ext = "." + normalizeAlgorithm(algorithm).toLowerCase().replace("-", "") + "sum";
        Path sidecar = file.resolveSibling(file.getFileName().toString() + ext);
        String line = String.format("%s  %s%n", digest, file.getFileName());

        try (BufferedWriter writer = Files.newBufferedWriter(sidecar, StandardCharsets.UTF_8)) {
            writer.write(line);
        }
        return sidecar;
    }

    /**
     * Writes a digest sidecar file for multiple files.
     * Each line contains: &lt;digest&gt;  &lt;filename&gt;
     *
     * @param files             Array of files to hash.
     * @param algorithm         Hash algorithm name or alias.
     * @param sidecarOutputFile Path to output sidecar file.
     * @return Path to the created sidecar file.
     * @throws IOException              If writing fails.
     * @throws IllegalArgumentException If algorithm is not supported.
     */
    public static Path writeDigestSidecar(Path[] files, String algorithm, Path sidecarOutputFile) throws IOException {
        validateAlgorithm(algorithm);
        Objects.requireNonNull(files);
        Objects.requireNonNull(sidecarOutputFile);

        try (BufferedWriter writer = Files.newBufferedWriter(sidecarOutputFile, StandardCharsets.UTF_8)) {
            for (Path file : files) {
                if (file == null || !Files.isReadable(file)) continue;
                String digest = digest(file, algorithm);
                writer.write(String.format("%s  %s%n", digest, file.getFileName()));
            }
        }
        return sidecarOutputFile;
    }

    /**
     * Verifies a file against its sidecar digest file with given algorithm.
     *
     * @param file      File to verify.
     * @param sidecar   Sidecar file containing digest.
     * @param algorithm Hash algorithm name or alias.
     * @return True if digest matches; false otherwise.
     * @throws IOException              If reading fails.
     * @throws IllegalArgumentException If algorithm is not supported.
     */
    public static boolean verifyWithSidecar(Path file, Path sidecar, String algorithm) throws IOException {
        String expected = readFirstHashFromSidecar(sidecar);
        return verifyFile(file, expected, algorithm);
    }

    /**
     * Verifies a file against its sidecar digest file.
     * Algorithm is inferred from sidecar filename extension.
     *
     * @param file    File to verify.
     * @param sidecar Sidecar file containing digest.
     * @return True if digest matches; false otherwise.
     * @throws IOException              If reading fails.
     * @throws IllegalArgumentException If algorithm cannot be inferred.
     */
    public static boolean verifyWithSidecar(Path file, Path sidecar) throws IOException {
        String algorithm = inferAlgorithmFromFilename(sidecar);
        if (algorithm == null) {
            throw new IllegalArgumentException("Cannot infer algorithm from file name: " + sidecar);
        }
        return verifyWithSidecar(file, sidecar, algorithm);
    }

    /**
     * Verifies all files listed in a multi-line digest sidecar file.
     * Each line should have format: &lt;digest&gt;  &lt;filename&gt;
     * Lines that are empty or start with '#' are ignored.
     * Files are assumed to be located in the same directory as the sidecar file.
     *
     * @param sidecar Path to the sidecar file.
     * @return {@code true} if all files exist and match their respective digests; {@code false} otherwise.
     * @throws IOException              If reading sidecar or files fails.
     * @throws IllegalArgumentException If algorithm cannot be inferred from sidecar filename.
     */
    public static boolean verifyWithSidecarBatch(Path sidecar) throws IOException {
        Map<Path, Boolean> details = verifyWithSidecarBatchDetailed(sidecar);
        return details.values().stream().allMatch(Boolean::booleanValue);
    }

    public static Map<Path, Boolean> verifyWithSidecarBatchDetailed(Path sidecar) throws IOException {
        Objects.requireNonNull(sidecar);
        String algorithm = inferAlgorithmFromFilename(sidecar);
        if (algorithm == null) {
            throw new IllegalArgumentException("Cannot infer algorithm from sidecar filename: " + sidecar);
        }

        Path baseDir = sidecar.getParent();
        if (baseDir == null) {
            baseDir = sidecar.getFileSystem().getPath(".");
        }

        Map<Path, Boolean> details = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(sidecar, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+", 2);
                if (parts.length < 2) {
                    // Malformed line, consider verification failed
                    continue;
                }
                String expectedDigest = parts[0];
                String filename = parts[1];

                Path filePath = baseDir.resolve(filename);
                if (!Files.exists(filePath) || !verifyFile(filePath, expectedDigest, algorithm)) {
                    details.put(filePath, false);
                    continue;
                }
                details.put(filePath, true);
            }
        }

        return details;
    }


    /**
     * Reads the first valid hash from the sidecar file.
     * Skips empty lines and lines starting with '#'.
     *
     * @param sidecar Sidecar file path.
     * @return Digest string, or null if none found.
     * @throws IOException If reading fails.
     */
    public static String readFirstHashFromSidecar(Path sidecar) throws IOException {
        try (Reader reader = Files.newBufferedReader(sidecar, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.matches("^[0-9a-fA-F]{32,128}\\s+.*$")) {
                    return line.split("\\s+")[0];
                }
            }
        }
        return null;
    }

    /**
     * Converts byte array to hexadecimal string.
     *
     * @param bytes     Input byte array.
     * @param uppercase Whether to output uppercase hex letters.
     * @return Hexadecimal string or null if input is null.
     */
    public static String toHex(byte[] bytes, boolean uppercase) {
        if (bytes == null) return null;
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            hex[i * 2] = HEX_CHARS[(b >>> 4)];
            hex[i * 2 + 1] = HEX_CHARS[(b & 0x0F)];
        }
        String result = new String(hex);
        return uppercase ? result.toUpperCase() : result;
    }

    /**
     * Converts byte array to lowercase hexadecimal string.
     *
     * @param bytes Input byte array.
     * @return Hexadecimal string or null if input is null.
     */
    public static String toHex(byte[] bytes) {
        return toHex(bytes, false);
    }

    /**
     * Infers hash algorithm from a sidecar file's filename extension.
     * Recognizes .md5sum, .sha1sum, .sha256sum, and .sha512sum extensions.
     *
     * @param file Sidecar file path.
     * @return Algorithm name or null if unrecognized.
     */
    private static String inferAlgorithmFromFilename(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".md5sum")) return "MD5";
        if (name.endsWith(".sha1sum")) return "SHA-1";
        if (name.endsWith(".sha256sum")) return "SHA-256";
        if (name.endsWith(".sha512sum")) return "SHA-512";
        return null;
    }

    // =============================
    // Convenience methods for common algorithms
    // =============================

    /**
     * Computes the MD5 digest of a file.
     *
     * @param file Path to file.
     * @return MD5 hex digest string or null if file unreadable.
     * @throws IOException If file IO fails.
     */
    public static String md5(Path file) throws IOException {
        return digest(file, "MD5");
    }

    /**
     * Computes the MD5 digest of a UTF-8 string.
     *
     * @param text Input text.
     * @return MD5 hex digest string or null if input null.
     */
    public static String md5(String text) {
        return digest(text, "MD5");
    }

    /**
     * Computes the MD5 digest of a byte array.
     *
     * @param bytes Input bytes.
     * @return MD5 hex digest string or null if input null.
     */
    public static String md5(byte[] bytes) {
        return digest(bytes, "MD5");
    }

    /**
     * Computes the MD5 digest of a byte array with uppercase hex option.
     *
     * @param bytes     Input bytes.
     * @param uppercase Whether to output uppercase hex.
     * @return MD5 hex digest string.
     */
    public static String md5(byte[] bytes, boolean uppercase) {
        byte[] raw = digestRaw(bytes, "MD5");
        return toHex(raw, uppercase);
    }

    /**
     * Computes the SHA-256 digest of a file.
     *
     * @param file Path to file.
     * @return SHA-256 hex digest string or null if file unreadable.
     * @throws IOException If file IO fails.
     */
    public static String sha256(Path file) throws IOException {
        return digest(file, "SHA-256");
    }

    /**
     * Computes the SHA-256 digest of a UTF-8 string.
     *
     * @param text Input text.
     * @return SHA-256 hex digest string or null if input null.
     */
    public static String sha256(String text) {
        return digest(text, "SHA-256");
    }

    /**
     * Computes the SHA-256 digest of a byte array.
     *
     * @param bytes Input bytes.
     * @return SHA-256 hex digest string or null if input null.
     */
    public static String sha256(byte[] bytes) {
        return digest(bytes, "SHA-256");
    }

    /**
     * Computes the SHA-256 digest of a byte array with uppercase hex option.
     *
     * @param bytes     Input bytes.
     * @param uppercase Whether to output uppercase hex.
     * @return SHA-256 hex digest string.
     */
    public static String sha256(byte[] bytes, boolean uppercase) {
        byte[] raw = digestRaw(bytes, "SHA-256");
        return toHex(raw, uppercase);
    }

    /**
     * Computes the SHA-512 digest of a file.
     *
     * @param file Path to file.
     * @return SHA-512 hex digest string or null if file unreadable.
     * @throws IOException If file IO fails.
     */
    public static String sha512(Path file) throws IOException {
        return digest(file, "SHA-512");
    }

    /**
     * Computes the SHA-512 digest of a UTF-8 string.
     *
     * @param text Input text.
     * @return SHA-512 hex digest string or null if input null.
     */
    public static String sha512(String text) {
        return digest(text, "SHA-512");
    }

    /**
     * Computes the SHA-512 digest of a byte array.
     *
     * @param bytes Input bytes.
     * @return SHA-512 hex digest string or null if input null.
     */
    public static String sha512(byte[] bytes) {
        return digest(bytes, "SHA-512");
    }

    /**
     * Computes the SHA-512 digest of a byte array with uppercase hex option.
     *
     * @param bytes     Input bytes.
     * @param uppercase Whether to output uppercase hex.
     * @return SHA-512 hex digest string.
     */
    public static String sha512(byte[] bytes, boolean uppercase) {
        byte[] raw = digestRaw(bytes, "SHA-512");
        return toHex(raw, uppercase);
    }
}
