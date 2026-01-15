package io.github.hiant.common.utils;

import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * Production-grade stateless desensitization utility class.
 * Core features: zero dependencies, thread-safe, regex precompilation, enum singleton immutable configuration,
 * high concurrency optimization, compatible with Java 21 virtual threads, SHA-256 instance reuse.
 * <p>
 * This utility provides semantic APIs for common sensitive data (phone, bank card) and general desensitization,
 * supports hash/checksum attachment for internal tracing, and avoids pseudo-sharing in ultra-high concurrency scenarios.
 */
public final class DesensitizationUtils {

    /**
     * Precompiled pattern for mainland China phone number validation (11 digits, starts with 1).
     * Reused globally to avoid repeated compilation and reduce CPU overhead.
     */
    private static final Pattern PHONE_PATTERN     = Pattern.compile("^1[3-9]\\d{9}$");

    /**
     * Precompiled pattern for bank card number validation (16 to 19 digits).
     * Reused globally to avoid repeated compilation and reduce CPU overhead.
     */
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("^\\d{16,19}$");

    /**
     * System property key for the default mask ratio used by {@link #desensitize(String, int, int, String)}.
     * <p>
     * If not configured (or configured to a non-positive value), the mask-ratio validation is disabled.
     * Example: {@code -Ddesensitize.maskRatio.default=0.6}
     */
    private static final String  SYS_PROP_MASK_RATIO_DEFAULT = "desensitize.maskRatio.default";

    // Private constructor to prevent instantiation
    private DesensitizationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ================================================================================
    // Enum Singleton Immutable Configuration (Natural Anti-Reflection, Simplified Code)
    // ================================================================================

    /**
     * Immutable desensitization configuration (enum singleton).
     * <p>
     * Natural protection against reflection and serialization, simpler code than DCL singleton.
     * Provides default values for placeholder, prefix/suffix retention length, and high concurrency switch.
     * Values can be overridden by system properties, with lazy initialization (enum instance is loaded on first use).
     */
    public enum DesensitizeConfig {
        /**
         * Singleton instance of desensitization configuration (only one instance globally).
         */
        INSTANCE;

        // System property keys for configuration override
        private static final String SYS_PROP_PLACEHOLDER          = "desensitize.placeholder";
        private static final String SYS_PROP_KEEP_PREFIX          = "desensitize.keepPrefix";
        private static final String SYS_PROP_KEEP_SUFFIX          = "desensitize.keepSuffix";
        private static final String SYS_PROP_DISABLE_THREAD_LOCAL = "desensitize.disableThreadLocal";

        // Immutable configuration properties (initialized in constructor, cannot be modified after)
        public final String         defaultPlaceholder;
        public final int            defaultKeepPrefix;
        public final int            defaultKeepSuffix;
        public final boolean        disableThreadLocal;

        /**
         * Enum constructor (automatically called once when the enum is first loaded).
         * Loads configuration from system properties, with default values as fallback.
         * Natural anti-reflection: Java does not allow reflection to create enum instances.
         */
        DesensitizeConfig() {
            // Load default placeholder
            String placeholder = System.getProperty(SYS_PROP_PLACEHOLDER);
            this.defaultPlaceholder = (placeholder != null && !placeholder.isEmpty()) ? placeholder : "****";

            // Load default keep prefix
            int keepPrefix = 3;
            try {
                String prefixProp = System.getProperty(SYS_PROP_KEEP_PREFIX);
                if (prefixProp != null) {
                    int propValue = Integer.parseInt(prefixProp);
                    keepPrefix = propValue >= 0 ? propValue : 3;
                }
            } catch (NumberFormatException ignored) {}
            this.defaultKeepPrefix = keepPrefix;

            // Load default keep suffix
            int keepSuffix = 2;
            try {
                String suffixProp = System.getProperty(SYS_PROP_KEEP_SUFFIX);
                if (suffixProp != null) {
                    int propValue = Integer.parseInt(suffixProp);
                    keepSuffix = propValue >= 0 ? propValue : 2;
                }
            } catch (NumberFormatException ignored) {}
            this.defaultKeepSuffix = keepSuffix;

            // Load high concurrency switch (disable ThreadLocal for lock-free instance creation)
            this.disableThreadLocal = Boolean.getBoolean(SYS_PROP_DISABLE_THREAD_LOCAL);
        }

        /**
         * Get singleton configuration instance (compatible with original API, more user-friendly).
         *
         * @return Immutable DesensitizeConfig singleton instance
         */
        public static DesensitizeConfig defaults() {
            return INSTANCE;
        }
    }

    // ================================================================================
    // ThreadLocal Caches (With SHA-256 Cache, Avoid Repeated Instance Creation)
    // ================================================================================

    /**
     * ThreadLocal cache for MD5 MessageDigest instances.
     * Reduces instance creation overhead in normal concurrency scenarios.
     */
    private static final ThreadLocal<MessageDigest> MD5_THREAD_LOCAL     = ThreadLocal.withInitial(() -> {
                                                                             try {
                                                                                 return MessageDigest
                                                                                     .getInstance(HashAlgorithm.MD5.getAlgorithmName());
                                                                             } catch (NoSuchAlgorithmException e) {
                                                                                 throw new IllegalStateException(
                                                                                     "MD5 algorithm is not supported by the JDK environment", e);
                                                                             }
                                                                         });

    /**
     * ThreadLocal cache for SHA-256 MessageDigest instances.
     * Optimizes performance: avoids repeated new instances in normal concurrency scenarios (same as MD5).
     */
    private static final ThreadLocal<MessageDigest> SHA_256_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
                                                                             try {
                                                                                 return MessageDigest
                                                                                     .getInstance(HashAlgorithm.SHA_256.getAlgorithmName());
                                                                             } catch (NoSuchAlgorithmException e) {
                                                                                 throw new IllegalStateException(
                                                                                     "SHA-256 algorithm is not supported by the JDK environment", e);
                                                                             }
                                                                         });

    /**
     * ThreadLocal cache for CRC32 instances.
     * Reduces instance creation overhead in normal concurrency scenarios.
     */
    private static final ThreadLocal<CRC32>         CRC32_THREAD_LOCAL   = ThreadLocal.withInitial(CRC32::new);

    /**
     * ThreadLocal cache for CRC16 instances.
     * Reduces instance creation overhead in normal concurrency scenarios.
     */
    private static final ThreadLocal<CRC16>         CRC16_THREAD_LOCAL   = ThreadLocal.withInitial(CRC16::new);

    // Shutdown hook for ThreadLocal cache cleanup (fallback for normal JVM shutdown)
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(DesensitizationUtils::cleanThreadLocalCache, "DesensitizationUtils-ThreadLocal-Cleaner"));
    }

    /**
     * Manually clean ThreadLocal caches to prevent memory leaks.
     * <p>
     * Recommended to be called in the finally block of thread pool tasks (including virtual threads)
     * and before web container hot deployment/shutdown.
     */
    public static void cleanThreadLocalCache() {
        MD5_THREAD_LOCAL.remove();
        SHA_256_THREAD_LOCAL.remove();
        CRC32_THREAD_LOCAL.remove();
        CRC16_THREAD_LOCAL.remove();
    }

    // ================================================================================
    // Hash/Checksum Algorithm Enumeration
    // ================================================================================

    /**
     * Enumeration of supported hash/checksum algorithms.
     * Contains algorithm name and functional description for clear usage distinction.
     */
    @Getter
    public enum HashAlgorithm {
        MD5("MD5", "Cryptographic hash, balanced speed, length and collision resistance"),
        SHA_256("SHA-256", "Cryptographic hash, high collision resistance, longer output length"),
        CRC32("CRC32", "Checksum, high speed, 8-byte hex output, low collision resistance"),
        CRC16("CRC16", "Checksum, ultra high speed, 4-byte hex output, high collision probability");

        /**
         * Algorithm name (compatible with MessageDigest/CRC APIs)
         */
        private final String algorithmName;
        /**
         * Algorithm functional description
         */
        private final String description;

        HashAlgorithm(String algorithmName, String description) {
            this.algorithmName = algorithmName;
            this.description = description;
        }

    }

    // ================================================================================
    // Lightweight CRC16 Implementation (CRC16-CCITT Standard)
    // ================================================================================

    /**
     * Lightweight CRC16 implementation (CRC16-CCITT standard).
     * <p>
     * Not thread-safe on its own; intended to be used with ThreadLocal or new instance creation per task.
     * State machine is lightweight with minimal instantiation overhead.
     */
    private static class CRC16 {
        private static final int POLYNOMIAL = 0x1021; // CRC16-CCITT polynomial
        private int              crc        = 0xFFFF; // Initial value

        /**
         * Reset CRC16 state to initial value.
         * Required for instance reuse to avoid data contamination.
         */
        public void reset() {
            crc = 0xFFFF;
        }

        /**
         * Calculate CRC16 checksum for the given byte array.
         *
         * @param bytes
         *            Input byte array
         * @return 16-bit CRC16 checksum (range: 0-65535)
         */
        public int calculate(byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return crc;
            }

            for (byte b : bytes) {
                for (int i = 0; i < 8; i++) {
                    boolean bit = ((b >> (7 - i) & 1) == 1);
                    boolean c15 = ((crc >> 15 & 1) == 1);
                    crc <<= 1;
                    if (c15 ^ bit) {
                        crc ^= POLYNOMIAL;
                    }
                }
            }

            crc &= 0xFFFF; // Restrict to 16-bit range
            return crc;
        }
    }

    // ================================================================================
    // Semantic APIs (Phone/Bank Card)
    // ================================================================================

    /**
     * Desensitize mainland China phone number (11 digits).
     * <p>
     * Retains 3 prefix digits and 4 suffix digits with default placeholder.
     * Uses precompiled regex for validation to optimize performance.
     *
     * @param phone
     *            Input mainland China phone number (11 digits, starts with 1)
     * @return Desensitized phone number (format: 138****5678)
     * @throws IllegalArgumentException
     *             If phone number is null or does not match the pattern
     */
    public static String phone(String phone) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("Invalid phone number format: must be 11 digits starting with 1");
        }

        DesensitizeConfig config = DesensitizeConfig.defaults();
        return desensitizeInternal(phone, 3, 4, config.defaultPlaceholder);
    }

    /**
     * Desensitize mainland China phone number with default MD5 hash.
     * <p>
     * Retains 3 prefix digits and 4 suffix digits, appends MD5 hash for internal tracing.
     *
     * @param phone
     *            Input mainland China phone number (11 digits, starts with 1)
     * @return Desensitized phone number with MD5 hash (format: 138****5678(3e25960a...))
     * @throws IllegalArgumentException
     *             If phone number is null or does not match the pattern
     */
    public static String phoneWithHash(String phone) {
        return phoneWithHash(phone, HashAlgorithm.MD5);
    }

    /**
     * Desensitize a mainland China mobile phone number with the specified hash algorithm.
     * <p>
     * Retains 3 prefix digits and 4 suffix digits, appends the specified cryptographic hash for internal tracing.
     * CRC32/CRC16 are prohibited for phone numbers to avoid security risks and low collision resistance.
     *
     * @param phone
     *            Input mainland China mobile phone number (11 digits, starts with 1)
     * @param hashAlgorithm
     *            Hash algorithm (MD5/SHA-256). If null, defaults to MD5.
     * @return Desensitized phone number with hash (format: 138****5678(xxx...))
     * @throws IllegalArgumentException
     *             If phone number is invalid, or CRC32/CRC16 is used
     */
    public static String phoneWithHash(String phone, HashAlgorithm hashAlgorithm) {
        String desensitizedPhone = phone(phone);
        DesensitizeConfig config = DesensitizeConfig.defaults();

        if (hashAlgorithm == null) {
            hashAlgorithm = HashAlgorithm.MD5;
        }

        if (hashAlgorithm == HashAlgorithm.CRC32 || hashAlgorithm == HashAlgorithm.CRC16) {
            throw new IllegalArgumentException("CRC32/CRC16 is not allowed for phone number desensitization");
        }

        return desensitizedPhone +
               "(" +
               generateHash(phone, hashAlgorithm, config) +
               ")";
    }

    /**
     * Desensitize bank card number (16 to 19 digits).
     * <p>
     * Retains 6 prefix digits and 4 suffix digits with default placeholder.
     * Uses precompiled regex for validation to optimize performance.
     *
     * @param bankCard
     *            Input bank card number (16 to 19 digits)
     * @return Desensitized bank card number (format: 622260****1234)
     * @throws IllegalArgumentException
     *             If bank card number is null or does not match the pattern
     */
    public static String bankCard(String bankCard) {
        if (bankCard == null || !BANK_CARD_PATTERN.matcher(bankCard).matches()) {
            throw new IllegalArgumentException("Invalid bank card format: must be 16 to 19 digits");
        }

        DesensitizeConfig config = DesensitizeConfig.defaults();
        return desensitizeInternal(bankCard, 6, 4, config.defaultPlaceholder);
    }

    /**
     * Desensitize bank card number with default MD5 hash.
     * <p>
     * Retains 6 prefix digits and 4 suffix digits, appends MD5 hash for internal tracing.
     *
     * @param bankCard
     *            Input bank card number (16 to 19 digits)
     * @return Desensitized bank card number with MD5 hash (format: 622260****1234(3e25960a...))
     * @throws IllegalArgumentException
     *             If bank card number is null or does not match the pattern
     */
    public static String bankCardWithHash(String bankCard) {
        return bankCardWithHash(bankCard, HashAlgorithm.MD5);
    }

    /**
     * Desensitize bank card number with specified hash algorithm.
     * <p>
     * Retains 6 prefix digits and 4 suffix digits, appends specified hash/checksum for internal tracing.
     * Prohibits CRC32/CRC16 for sensitive bank card data to avoid security risks.
     *
     * @param bankCard
     *            Input bank card number (16 to 19 digits)
     * @param hashAlgorithm
     *            Specified hash/checksum algorithm
     * @return Desensitized bank card number with specified hash (format: 622260****1234(xxx...))
     * @throws IllegalArgumentException
     *             If bank card is invalid, algorithm is null, or CRC32/CRC16 is used
     */
    public static String bankCardWithHash(String bankCard, HashAlgorithm hashAlgorithm) {
        String desensitizedBankCard = bankCard(bankCard);
        DesensitizeConfig config = DesensitizeConfig.defaults();

        if (hashAlgorithm == null) {
            hashAlgorithm = HashAlgorithm.MD5;
        }

        if (hashAlgorithm == HashAlgorithm.CRC32 || hashAlgorithm == HashAlgorithm.CRC16) {
            throw new IllegalArgumentException("CRC32/CRC16 is not allowed for bank card desensitization");
        }

        return desensitizedBankCard +
               "(" +
               generateHash(bankCard, hashAlgorithm, config) +
               ")";
    }

    /**
     * Desensitize email address.
     * <p>
     * Masks the local part of email while preserving domain.
     * For local part: keeps first character and masks the rest.
     * Example: "john.doe@example.com" becomes "j****@example.com"
     *
     * @param email
     *            Input email address
     * @return Desensitized email address
     * @throws IllegalArgumentException
     *             If email is null or does not contain '@'
     */
    public static String desensitizeEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format: must contain '@'");
        }

        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (localPart.length() <= 1) {
            return localPart + "****" + domain;
        }

        DesensitizeConfig config = DesensitizeConfig.defaults();
        return localPart.charAt(0) + config.defaultPlaceholder + domain;
    }

    // ================================================================================
    // General Desensitization APIs (Overloaded)
    // ================================================================================

    /**
     * General desensitization with default configuration (placeholder, prefix/suffix retention).
     *
     * @param content
     *            Input content to be desensitized
     * @return Desensitized content
     * @throws IllegalArgumentException
     *             If content is null or retention lengths are negative
     */
    public static String desensitize(String content) {
        DesensitizeConfig config = DesensitizeConfig.defaults();
        return desensitize(content, config.defaultKeepPrefix, config.defaultKeepSuffix, config.defaultPlaceholder);
    }

    /**
     * General desensitization with specified prefix/suffix retention and default placeholder.
     *
     * @param content
     *            Input content to be desensitized
     * @param keepPrefix
     *            Number of prefix characters to retain
     * @param keepSuffix
     *            Number of suffix characters to retain
     * @return Desensitized content
     * @throws IllegalArgumentException
     *             If content is null or retention lengths are negative
     */
    public static String desensitize(String content, int keepPrefix, int keepSuffix) {
        DesensitizeConfig config = DesensitizeConfig.defaults();
        return desensitize(content, keepPrefix, keepSuffix, config.defaultPlaceholder);
    }

    /**
     * Core general desensitization method with full custom parameters.
     * <p>
     * Uses code point counting to handle emojis and multi-byte characters correctly.
     * <p>
     * Mask-ratio validation is configurable and disabled by default:
     * when the system property {@code desensitize.maskRatio.default} is configured to a positive number,
     * the masked area must occupy at least that ratio of the total code points. When not configured (or configured
     * to a non-positive / invalid value), no mask-ratio validation is performed.
     *
     * @param content
     *            Input content to be desensitized
     * @param keepPrefix
     *            Number of prefix code points to retain (≥ 0)
     * @param keepSuffix
     *            Number of suffix code points to retain (≥ 0)
     * @param placeholder
     *            Placeholder string for masked area
     * @return Desensitized content (format: prefix + placeholder + suffix)
     * @throws IllegalArgumentException
     *             If retention lengths are negative, or the configured mask ratio is insufficient
     */
    public static String desensitize(String content, int keepPrefix, int keepSuffix, String placeholder) {
        // Input validation (fail-fast)
        Objects.requireNonNull(content, "Content cannot be null");

        if (keepPrefix < 0) {
            throw new IllegalArgumentException("keepPrefix cannot be negative: " + keepPrefix);
        }

        if (keepSuffix < 0) {
            throw new IllegalArgumentException("keepSuffix cannot be negative: " + keepSuffix);
        }

        // Code point counting (handle emojis/multi-byte characters) - computed once
        int totalCodePoints = content.codePointCount(0, content.length());

        // Fail-fast: return original content immediately if retention exceeds total length
        if (keepPrefix + keepSuffix >= totalCodePoints) {
            return content;
        }

        // Validate mask ratio only when configured (disabled by default)
        double requiredMaskRatio = parseMaskRatio(System.getProperty(SYS_PROP_MASK_RATIO_DEFAULT));
        validateMaskRatio(content, keepPrefix, keepSuffix, requiredMaskRatio);

        // Delegate to core method with pre-computed totalCodePoints
        return desensitizeCore(content, keepPrefix, keepSuffix, placeholder, totalCodePoints);
    }

    /**
     * Internal desensitization method without 60% mask ratio check.
     * <p>
     * Used by semantic APIs (phone, bankCard) that have fixed format requirements
     * and should not be subject to the general 60% mask ratio constraint.
     *
     * @param content
     *            Input content to be desensitized
     * @param keepPrefix
     *            Number of prefix code points to retain (≥ 0)
     * @param keepSuffix
     *            Number of suffix code points to retain (≥ 0)
     * @param placeholder
     *            Placeholder string for masked area
     * @return Desensitized content (format: prefix + placeholder + suffix)
     */
    private static String desensitizeInternal(String content, int keepPrefix, int keepSuffix, String placeholder) {
        int totalCodePoints = content.codePointCount(0, content.length());

        // Fail-fast: return original content immediately if retention exceeds total length
        if (keepPrefix + keepSuffix >= totalCodePoints) {
            return content;
        }

        return desensitizeCore(content, keepPrefix, keepSuffix, placeholder, totalCodePoints);
    }

    /**
     * Desensitize content for preset types without 60% mask ratio check.
     * <p>
     * Used by {@link DesensitizeType} for semantic desensitization (phone, ID card, etc.)
     * where the retention lengths are predefined and validated.
     *
     * @param content
     *            Input content to be desensitized
     * @param keepPrefix
     *            Number of prefix code points to retain (non-negative)
     * @param keepSuffix
     *            Number of suffix code points to retain (non-negative)
     * @return Desensitized content (format: prefix + placeholder + suffix)
     */
    public static String desensitizeForPreset(String content, int keepPrefix, int keepSuffix) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        DesensitizeConfig config = DesensitizeConfig.defaults();
        return desensitizeInternal(content, keepPrefix, keepSuffix, config.defaultPlaceholder);
    }

    /**
     * Core desensitization logic with pre-computed totalCodePoints.
     * <p>
     * Avoids redundant codePointCount calculation. Called by both desensitize and desensitizeInternal.
     *
     * @param content
     *            Input content to be desensitized (already validated)
     * @param keepPrefix
     *            Number of prefix code points to retain
     * @param keepSuffix
     *            Number of suffix code points to retain
     * @param placeholder
     *            Placeholder string for masked area
     * @param totalCodePoints
     *            Pre-computed total code points count
     * @return Desensitized content (format: prefix + placeholder + suffix)
     */
    private static String desensitizeCore(String content, int keepPrefix, int keepSuffix, String placeholder, int totalCodePoints) {
        DesensitizeConfig config = DesensitizeConfig.defaults();
        String effectivePlaceholder = (placeholder != null && !placeholder.isEmpty()) ? placeholder : config.defaultPlaceholder;

        // Safe code point extraction
        int[] codePoints = content.codePoints().toArray();
        String prefix = new String(codePoints, 0, keepPrefix);
        String suffix = new String(codePoints, totalCodePoints - keepSuffix, keepSuffix);

        // Build and return desensitized content
        return prefix + effectivePlaceholder + suffix;
    }

    /**
     * General desensitization with specified hash algorithm for internal tracing.
     * <p>
     * Appends hash/checksum to desensitized content. Not recommended for external display.
     *
     * @param content
     *            Input content to be desensitized
     * @param keepPrefix
     *            Number of prefix code points to retain (≥ 0)
     * @param keepSuffix
     *            Number of suffix code points to retain (≥ 0)
     * @param placeholder
     *            Placeholder string for masked area
     * @param hashAlgorithm
     *            Specified hash/checksum algorithm
     * @return Desensitized content with appended hash (format: prefix+placeholder+suffix(hash))
     * @throws IllegalArgumentException
     *             If input parameters are invalid
     */
    public static String desensitizeWithHash(String content,
                                             int keepPrefix,
                                             int keepSuffix,
                                             String placeholder,
                                             HashAlgorithm hashAlgorithm) {
        String desensitizedContent = desensitize(content, keepPrefix, keepSuffix, placeholder);
        DesensitizeConfig config = DesensitizeConfig.defaults();

        if (hashAlgorithm == null) {
            hashAlgorithm = HashAlgorithm.MD5;
        }

        return desensitizedContent +
               "(" +
               generateHash(content, hashAlgorithm, config) +
               ")";
    }

    /**
     * General desensitization with default placeholder and MD5 hash for internal tracing.
     * <p>
     * Convenience overload using default configuration values.
     *
     * @param content
     *            Input content to be desensitized
     * @param keepPrefix
     *            Number of prefix code points to retain (non-negative)
     * @param keepSuffix
     *            Number of suffix code points to retain (non-negative)
     * @return Desensitized content with appended MD5 hash (format: prefix+placeholder+suffix(hash))
     * @throws IllegalArgumentException
     *             If input parameters are invalid
     */
    public static String desensitizeWithHash(String content, int keepPrefix, int keepSuffix) {
        DesensitizeConfig config = DesensitizeConfig.defaults();
        return desensitizeWithHash(content, keepPrefix, keepSuffix, config.defaultPlaceholder, HashAlgorithm.MD5);
    }

    // ================================================================================
    // Core Hash/Checksum Generation Methods (SHA-256 Reuses ThreadLocal Instance)
    // ================================================================================

    /**
     * Generate hash/checksum for the given content with specified algorithm.
     * <p>
     * Supports ThreadLocal reuse (normal concurrency) and new instance creation (ultra-high concurrency)
     * based on configuration switch. Resets instance state before reuse to avoid data contamination.
     *
     * @param content
     *            Input content for hash/checksum calculation
     * @param hashAlgorithm
     *            Specified hash/checksum algorithm
     * @param config
     *            Desensitization configuration (high concurrency switch)
     * @return Hex string representation of hash/checksum
     * @throws IllegalStateException
     *             If algorithm instance cannot be obtained
     */
    private static String generateHash(String content, HashAlgorithm hashAlgorithm, DesensitizeConfig config) {
        Objects.requireNonNull(content, "Content cannot be null");
        Objects.requireNonNull(hashAlgorithm, "HashAlgorithm cannot be null");

        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        switch (hashAlgorithm) {
            case CRC32:
                return handleCRC32(contentBytes, config);
            case CRC16:
                return handleCRC16(contentBytes, config);
            case MD5:
                return handleCryptographicHash(contentBytes, hashAlgorithm, MD5_THREAD_LOCAL, config);
            case SHA_256:
                return handleCryptographicHash(contentBytes, hashAlgorithm, SHA_256_THREAD_LOCAL, config);
            default:
                throw new IllegalArgumentException("Unsupported hash algorithm: " + hashAlgorithm);
        }
    }

    /**
     * Handle CRC32 checksum calculation (supports ultra-high concurrency optimization).
     *
     * @param contentBytes
     *            Input byte array
     * @param config
     *            Desensitization configuration (high concurrency switch)
     * @return 8-byte lower-case hex string of CRC32 checksum
     */
    private static String handleCRC32(byte[] contentBytes, DesensitizeConfig config) {
        CRC32 crc32;

        if (config.disableThreadLocal) {
            // Ultra-high concurrency: lock-free new instance (lower overhead than ThreadLocal hash lookup)
            crc32 = new CRC32();
        } else {
            // Normal concurrency: reuse ThreadLocal instance with state reset
            crc32 = CRC32_THREAD_LOCAL.get();
            crc32.reset();
        }

        crc32.update(contentBytes);
        long crc32Value = crc32.getValue();

        return String.format("%08X", crc32Value).toLowerCase();
    }

    /**
     * Handle CRC16 checksum calculation (supports ultra-high concurrency optimization).
     *
     * @param contentBytes
     *            Input byte array
     * @param config
     *            Desensitization configuration (high concurrency switch)
     * @return 4-byte lower-case hex string of CRC16 checksum
     */
    private static String handleCRC16(byte[] contentBytes, DesensitizeConfig config) {
        CRC16 crc16;

        if (config.disableThreadLocal) {
            // Ultra-high concurrency: lock-free new instance (lower overhead than ThreadLocal hash lookup)
            crc16 = new CRC16();
        } else {
            // Normal concurrency: reuse ThreadLocal instance with state reset
            crc16 = CRC16_THREAD_LOCAL.get();
            crc16.reset();
        }

        int crc16Value = crc16.calculate(contentBytes);

        return String.format("%04X", crc16Value).toLowerCase();
    }

    /**
     * Unified handler for cryptographic hashes (MD5/SHA-256) with ThreadLocal reuse.
     * <p>
     * Avoids repeated instance creation; resets instance state before calculation to prevent data contamination.
     *
     * @param contentBytes
     *            Input byte array
     * @param hashAlgorithm
     *            Hash algorithm enum (for algorithm name when creating new instance)
     * @param digestThreadLocal
     *            ThreadLocal cache of MessageDigest instances
     * @param config
     *            Desensitization configuration (high concurrency switch)
     * @return Lower-case hex string of cryptographic hash
     */
    private static String handleCryptographicHash(byte[] contentBytes,
                                                  HashAlgorithm hashAlgorithm,
                                                  ThreadLocal<MessageDigest> digestThreadLocal,
                                                  DesensitizeConfig config) {
        MessageDigest messageDigest;

        if (config.disableThreadLocal) {
            // Ultra-high concurrency: lock-free new instance using algorithm name directly
            messageDigest = createMessageDigest(hashAlgorithm.getAlgorithmName());
        } else {
            // Normal concurrency: reuse ThreadLocal instance (optimal performance)
            messageDigest = digestThreadLocal.get();
        }

        messageDigest.reset();
        byte[] hashBytes = messageDigest.digest(contentBytes);

        return convertBytesToHex(hashBytes);
    }

    /**
     * Create new MessageDigest instance by algorithm name.
     * <p>
     * Optimized for ultra-high concurrency: avoids ThreadLocal access overhead.
     *
     * @param algorithmName
     *            Algorithm name (e.g., "MD5", "SHA-256")
     * @return New MessageDigest instance
     * @throws IllegalStateException
     *             If algorithm instance cannot be created
     */
    private static MessageDigest createMessageDigest(String algorithmName) {
        try {
            return MessageDigest.getInstance(algorithmName);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to create MessageDigest instance for: " + algorithmName, e);
        }
    }

    /**
     * Convert byte array to lower-case hex string.
     * <p>
     * Compatible with all JDK versions (alternative to HexFormat in JDK 17+).
     *
     * @param bytes
     *            Input byte array
     * @return Lower-case hex string (empty string if input is null/empty)
     */
    private static String convertBytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder hexBuilder = new StringBuilder(bytes.length * 2);

        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexBuilder.append('0');
            }
            hexBuilder.append(hex);
        }

        return hexBuilder.toString().toLowerCase();
    }

    // ================================================================================
    // Strategy-Based Desensitization Entry Point
    // ================================================================================

    /**
     * Desensitize a value using a preset {@link DesensitizeType} resolved by name or alias.
     * <p>
     * The {@code typeName} is resolved by {@link DesensitizeType#fromName(String)} and supports:
     * case-insensitive matching, underscore/dash/space separators, camel-case names, and configured aliases
     * (e.g. {@code "MOBILE_PHONE"}, {@code "MobilePhone"}, {@code "msisdn"}).
     * <p>
     * Note: this is a strategy/preset entry point and therefore follows the same policy as
     * {@link #strategyDesensitize(String, DesensitizeType, OptionalInt, OptionalInt, boolean)}:
     * the mask-ratio constraint is enforced only when the corresponding system property is configured.
     *
     * @param typeName
     *            Type name or alias (e.g. {@code "mobile_phone"}, {@code "msisdn"})
     * @param value
     *            Raw value to desensitize
     * @return Desensitized value (or original when blank)
     * @throws IllegalArgumentException
     *             If {@code typeName} cannot be resolved to any {@link DesensitizeType}
     */
    public static String desensitize(String typeName, String value) {
        return strategyDesensitize(value, parseDesensitizeType(typeName), OptionalInt.empty(), OptionalInt.empty(), false);
    }

    /**
     * Desensitize a value using a preset {@link DesensitizeType} resolved by name or alias, with custom
     * prefix/suffix overrides.
     *
     * @param typeName
     *            Type name or alias (see {@link #desensitize(String, String)})
     * @param value
     *            Raw value to desensitize
     * @param keepPrefix
     *            Custom prefix code points to keep (non-negative)
     * @param keepSuffix
     *            Custom suffix code points to keep (non-negative)
     * @return Desensitized value (or original when blank)
     * @throws IllegalArgumentException
     *             If {@code keepPrefix}/{@code keepSuffix} are negative, or {@code typeName} cannot be resolved
     */
    public static String desensitize(String typeName, String value, int keepPrefix, int keepSuffix) {
        if (keepPrefix < 0) {
            throw new IllegalArgumentException("keepPrefix cannot be negative: " + keepPrefix);
        }
        if (keepSuffix < 0) {
            throw new IllegalArgumentException("keepSuffix cannot be negative: " + keepSuffix);
        }
        return strategyDesensitize(value, parseDesensitizeType(typeName), OptionalInt.of(keepPrefix), OptionalInt.of(keepSuffix), false);
    }

    /**
     * Desensitize a value using a preset {@link DesensitizeType} resolved by name or alias, and append an MD5 hash
     * for internal tracing.
     * <p>
     * This overload always appends an MD5 hash (lower-case hex, 32 chars). It is not intended for external display.
     *
     * @param typeName
     *            Type name or alias (see {@link #desensitize(String, String)})
     * @param value
     *            Raw value to desensitize
     * @return Desensitized value with appended hash (format: {@code masked(md5)})
     * @throws IllegalArgumentException
     *             If {@code typeName} cannot be resolved to any {@link DesensitizeType}
     */
    public static String desensitizeWithHash(String typeName, String value) {
        return strategyDesensitize(value, parseDesensitizeType(typeName), OptionalInt.empty(), OptionalInt.empty(), true);
    }

    /**
     * Desensitize a value using a preset {@link DesensitizeType} resolved by name or alias, with custom
     * prefix/suffix overrides, and append an MD5 hash for internal tracing.
     *
     * @param typeName
     *            Type name or alias (see {@link #desensitize(String, String)})
     * @param value
     *            Raw value to desensitize
     * @param keepPrefix
     *            Custom prefix code points to keep (non-negative)
     * @param keepSuffix
     *            Custom suffix code points to keep (non-negative)
     * @return Desensitized value with appended hash (format: {@code masked(md5)})
     * @throws IllegalArgumentException
     *             If {@code keepPrefix}/{@code keepSuffix} are negative, or {@code typeName} cannot be resolved
     */
    public static String desensitizeWithHash(String typeName, String value, int keepPrefix, int keepSuffix) {
        if (keepPrefix < 0) {
            throw new IllegalArgumentException("keepPrefix cannot be negative: " + keepPrefix);
        }
        if (keepSuffix < 0) {
            throw new IllegalArgumentException("keepSuffix cannot be negative: " + keepSuffix);
        }
        return strategyDesensitize(value, parseDesensitizeType(typeName), OptionalInt.of(keepPrefix), OptionalInt.of(keepSuffix), true);
    }

    /**
     * Strategy-based desensitization with preset type and custom configuration.
     * <p>
     * Combines preset type defaults with optional custom prefix/suffix overrides.
     * Supports optional hash appending for internal tracing.
     * Uses deep copy to defend against concurrent/in-place modification risks.
     *
     * @param value
     *            Original string value
     * @param type
     *            Preset desensitization type (defines default strategy and prefix/suffix)
     * @param customPrefix
     *            Custom prefix override (OptionalInt.empty() uses preset value)
     * @param customSuffix
     *            Custom suffix override (OptionalInt.empty() uses preset value)
     * @param withHash
     *            Whether to append hash (true: hash appended; false: placeholder only)
     * @return Desensitized string
     * @since JDK1.8
     */
    public static String strategyDesensitize(String value,
                                             DesensitizeType type,
                                             OptionalInt customPrefix,
                                             OptionalInt customSuffix,
                                             boolean withHash) {
        if (isBlank(value)) {
            return value;
        }
        Objects.requireNonNull(type, "DesensitizeType cannot be null");

        // Deep copy for safety against concurrent modification
        char[] rawChars = value.toCharArray();
        char[] safeChars = Arrays.copyOf(rawChars, rawChars.length);
        String safeValue = new String(safeChars);

        // Determine final prefix/suffix: custom overrides preset
        OptionalInt finalPrefix = customPrefix.isPresent() ? customPrefix : type.getDefaultPrefix();
        OptionalInt finalSuffix = customSuffix.isPresent() ? customSuffix : type.getDefaultSuffix();
        int prefix = finalPrefix.orElse(0);
        int suffix = finalSuffix.orElse(0);

        // Type-level dynamic mask-ratio policy: only validate when system property is configured.
        type.getConfiguredMaskRatio().ifPresent(ratio -> validateMaskRatio(safeValue, prefix, suffix, ratio));

        if (withHash) {
            DesensitizeConfig config = DesensitizeConfig.defaults();
            String masked = desensitizeInternal(safeValue, prefix, suffix, config.defaultPlaceholder);
            return masked + "(" + generateHash(safeValue, HashAlgorithm.MD5, config) + ")";
        }

        return type.getStrategy().mask(safeValue, finalPrefix, finalSuffix);
    }

    /**
     * Parse a mask ratio configuration value.
     * <p>
     * Returns {@code 0.0} when the input is null/blank/invalid or non-positive, which effectively disables validation.
     * Values greater than {@code 1.0} are clamped to {@code 1.0}.
     *
     * @param ratioValue
     *            Raw ratio value (e.g. {@code "0.6"})
     * @return Parsed ratio in {@code [0.0, 1.0]}
     */
    private static double parseMaskRatio(String ratioValue) {
        if (ratioValue == null) {
            return 0.0;
        }
        String trimmed = ratioValue.trim();
        if (trimmed.isEmpty()) {
            return 0.0;
        }
        try {
            double ratio = Double.parseDouble(trimmed);
            if (ratio <= 0.0) {
                return 0.0;
            }
            if (ratio > 1.0) {
                return 1.0;
            }
            return ratio;
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    /**
     * Validate that the mask area occupies at least the required ratio of the total code points.
     * <p>
     * A non-positive {@code requiredMaskRatio} disables validation.
     *
     * @param content
     *            Raw content (non-null)
     * @param keepPrefix
     *            Prefix code points to retain
     * @param keepSuffix
     *            Suffix code points to retain
     * @param requiredMaskRatio
     *            Required mask ratio in (0, 1], e.g. {@code 0.6} means "mask at least 60%"
     * @throws IllegalArgumentException
     *             If the mask ratio is insufficient
     */
    private static void validateMaskRatio(String content, int keepPrefix, int keepSuffix, double requiredMaskRatio) {
        if (requiredMaskRatio <= 0.0) {
            return;
        }
        int totalCodePoints = content.codePointCount(0, content.length());
        if (keepPrefix + keepSuffix >= totalCodePoints) {
            return;
        }
        int maskCodePoints = totalCodePoints - (keepPrefix + keepSuffix);
        if (maskCodePoints < totalCodePoints * requiredMaskRatio) {
            throw new IllegalArgumentException(
                String.format("Mask area (%d) must be at least %.0f%% of total code points (%d) (required >= %d)",
                    maskCodePoints, requiredMaskRatio * 100, totalCodePoints, (int) Math.ceil(totalCodePoints * requiredMaskRatio)));
        }
    }

    /**
     * Parse {@code typeName} into a {@link DesensitizeType}.
     *
     * @param typeName
     *            Type name or alias
     * @return Resolved {@link DesensitizeType}
     * @throws IllegalArgumentException
     *             If the name/alias cannot be resolved
     */
    private static DesensitizeType parseDesensitizeType(String typeName) {
        return DesensitizeType.fromName(typeName);
    }

    /**
     * Check whether a string is blank (null or empty).
     *
     * @param value
     *            Input value
     * @return true if null or empty, false otherwise
     */
    private static boolean isBlank(String value) {
        return value == null || "".equals(value);
    }

}
