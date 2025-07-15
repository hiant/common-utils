package io.github.hiant.common.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * A utility class for generating and validating TOTP (Time-based One-Time Passwords)
 * based on the RFC 6238 specification.
 * <p>
 * Supports Base32-encoded secrets, multiple HMAC algorithms (e.g. HmacSHA1, HmacSHA256),
 * customizable time step (period), digit length, and time window tolerance.
 * </p>
 */
public final class TotpUtils {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] DIGITS_POW = {
            1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000
    };
    private static final int[] BASE32_LOOKUP = new int[128];
    private static final int MAX_SKEW = 5;
    private static final ThreadLocal<CachedMac> MAC_CACHE = new ThreadLocal<>();

    static {
        for (int i = 0; i < BASE32_LOOKUP.length; i++) {
            BASE32_LOOKUP[i] = -1;
        }
        for (int i = 0; i < BASE32_ALPHABET.length(); i++) {
            BASE32_LOOKUP[BASE32_ALPHABET.charAt(i)] = i;
        }
    }

    private TotpUtils() {
        // Utility class; prevent instantiation
    }

    /* ======= Default Overloads ======= */

    /**
     * Generates a 6-digit TOTP using default parameters (60s, HmacSHA1).
     *
     * @param base32Secret Base32-encoded secret key
     * @return 6-digit OTP
     */
    public static String generateTotp(String base32Secret) {
        return generateTotp(base32Secret, Instant.now().getEpochSecond(), 60, 6, "HmacSHA1");
    }

    /**
     * Generates a TOTP with custom digit length.
     *
     * @param base32Secret Base32-encoded secret key
     * @param digits       Number of OTP digits (6–8)
     * @return OTP string
     */
    public static String generateTotp(String base32Secret, int digits) {
        return generateTotp(base32Secret, Instant.now().getEpochSecond(), 60, digits, "HmacSHA1");
    }

    /**
     * Validates an OTP using default parameters (6-digit, 60s, ±1 skew).
     *
     * @param base32Secret Base32-encoded secret key
     * @param otp          User-supplied OTP
     * @return True if valid
     */
    public static boolean verifyTotp(String base32Secret, String otp) {
        return verifyTotp(base32Secret, otp, 60, 6, "HmacSHA1", 1);
    }

    /**
     * Validates an OTP with a custom skew value.
     *
     * @param base32Secret Base32-encoded secret key
     * @param otp          User-supplied OTP
     * @param skew         Allowed time steps (± skew * period)
     * @return True if valid
     */
    public static boolean verifyTotp(String base32Secret, String otp, int skew) {
        return verifyTotp(base32Secret, otp, 60, 6, "HmacSHA1", skew);
    }

    /* ======= Public API ======= */

    /**
     * Generates a TOTP code at a specific timestamp.
     *
     * @param base32Secret Base32-encoded secret key
     * @param time         Unix time in seconds
     * @param period       TOTP period in seconds (e.g. 60)
     * @param digits       OTP length (6–8)
     * @param algo         HMAC algorithm (e.g. HmacSHA1, HmacSHA256)
     * @return OTP string
     */
    public static String generateTotp(String base32Secret, long time, int period, int digits, String algo) {
        if (base32Secret == null || base32Secret.trim().isEmpty()) {
            throw new IllegalArgumentException("Secret key cannot be null or empty");
        }
        validateDigits(digits);

        byte[] key = decodeBase32(base32Secret);
        long counter = time / period;
        return generateHotp(key, counter, digits, algo);
    }

    /**
     * Verifies an OTP with given configuration and time skew tolerance.
     *
     * @param base32Secret Base32-encoded secret key
     * @param otp          User-supplied OTP
     * @param period       Period in seconds (e.g. 60)
     * @param digits       OTP length (6–8)
     * @param algo         HMAC algorithm
     * @param skew         Time-step skew allowed (±)
     * @return True if valid
     */
    public static boolean verifyTotp(String base32Secret, String otp, int period,
                                     int digits, String algo, int skew) {
        validateDigits(digits);
        validateSkew(skew);
        if (skew > MAX_SKEW) skew = MAX_SKEW;

        long now = Instant.now().getEpochSecond();
        return verifyTotpInRange(base32Secret, otp, period, digits, algo,
                now - skew * period, now + skew * period);
    }

    /**
     * Verifies OTP within an explicit time range.
     *
     * @param base32Secret Base32-encoded secret key
     * @param otp          User-supplied OTP
     * @param period       Period in seconds
     * @param digits       OTP length
     * @param algo         HMAC algorithm
     * @param startTime    Unix time in seconds (inclusive)
     * @param endTime      Unix time in seconds (inclusive)
     * @return True if any OTP in the time window matches
     */
    public static boolean verifyTotpInRange(String base32Secret, String otp, int period,
                                            int digits, String algo, long startTime, long endTime) {
        validateDigits(digits);
        validateTimeWindow(startTime, endTime);

        long startCounter = startTime / period;
        long endCounter = endTime / period;
        byte[] key = decodeBase32(base32Secret);

        for (long counter = startCounter; counter <= endCounter; counter++) {
            String candidate = generateHotp(key, counter, digits, algo);
            if (safeEquals(candidate, otp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clears the ThreadLocal cache for Mac instances.
     * Should be used in thread pools to prevent memory leaks.
     */
    public static void clearCache() {
        MAC_CACHE.remove();
    }

    /* ======= HOTP Generator ======= */

    private static String generateHotp(byte[] key, long counter, int digits, String algo) {
        try {
            Mac mac = getMacInstance(algo);
            mac.init(new SecretKeySpec(key, algo));

            byte[] counterBytes = new byte[8];
            for (int i = 7; i >= 0; i--) {
                counterBytes[i] = (byte) (counter & 0xFF);
                counter >>= 8;
            }

            byte[] hmac = mac.doFinal(counterBytes);
            int offset = hmac[hmac.length - 1] & 0x0F;

            int binary = ((hmac[offset] & 0x7F) << 24)
                    | ((hmac[offset + 1] & 0xFF) << 16)
                    | ((hmac[offset + 2] & 0xFF) << 8)
                    | (hmac[offset + 3] & 0xFF);

            int otp = binary % DIGITS_POW[digits];
            return padOtp(otp, digits);
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new OtpGenerationException("Failed to generate HOTP", e);
        }
    }

    private static String padOtp(int otp, int digits) {
        String value = Integer.toString(otp);
        if (value.length() >= digits) return value;

        StringBuilder sb = new StringBuilder(digits);
        for (int i = value.length(); i < digits; i++) {
            sb.append('0');
        }
        sb.append(value);
        return sb.toString();
    }

    /* ======= Base32 Decoder ======= */

    private static byte[] decodeBase32(String base32) {
        base32 = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        if (base32.isEmpty()) {
            throw new IllegalArgumentException("Secret key Base32 string is empty");
        }

        int buffer = 0, bitsLeft = 0, index = 0;
        byte[] result = new byte[base32.length() * 5 / 8];

        for (char c : base32.toCharArray()) {
            if (c >= BASE32_LOOKUP.length || BASE32_LOOKUP[c] == -1) {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }

            buffer = (buffer << 5) | BASE32_LOOKUP[c];
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }

        if (index != result.length) {
            byte[] truncated = new byte[index];
            System.arraycopy(result, 0, truncated, 0, index);
            return truncated;
        }

        return result;
    }

    /* ======= Constant-Time Comparison ======= */

    private static boolean safeEquals(String a, String b) {
        if (a == null || b == null) return a == b;

        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) return false;

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

    /* ======= Mac Instance Cache ======= */

    private static Mac getMacInstance(String algo) throws NoSuchAlgorithmException {
        CachedMac cached = MAC_CACHE.get();
        if (cached == null || !cached.algo.equalsIgnoreCase(algo)) {
            Mac mac = Mac.getInstance(algo);
            MAC_CACHE.set(new CachedMac(mac, algo));
            return mac;
        }
        return cached.mac;
    }

    private static class CachedMac {
        final Mac mac;
        final String algo;

        CachedMac(Mac mac, String algo) {
            this.mac = mac;
            this.algo = algo;
        }
    }

    /* ======= Input Validation ======= */

    private static void validateDigits(int digits) {
        if (digits < 6 || digits > 8) {
            throw new IllegalArgumentException("Digits must be between 6 and 8");
        }
    }

    private static void validateSkew(int skew) {
        if (skew < 0) {
            throw new IllegalArgumentException("Skew must be non-negative");
        }
    }

    private static void validateTimeWindow(long startTime, long endTime) {
        if (startTime > endTime) {
            throw new IllegalArgumentException("startTime must be <= endTime");
        }
    }

    /* ======= Custom Exception ======= */

    /**
     * Runtime exception thrown when OTP generation fails due to cryptographic errors.
     */
    public static class OtpGenerationException extends RuntimeException {
        public OtpGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
