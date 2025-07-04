package io.github.hiant.common.utils;

import java.security.SecureRandom;

/**
 * Utility class for generating secure and efficient random numbers.
 * This class uses {@link SecureRandom} for cryptographic strength randomness.
 */
public final class RandomUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private RandomUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Sets the seed value for the underlying {@link SecureRandom} instance.
     * This can be used to initialize the random number generator with a specific entropy source,
     * ensuring reproducibility of generated values for testing or deterministic scenarios.
     *
     * @param seed The seed bytes to set for the random number generator.
     */
    public static void setSeed(byte[] seed) {
        SECURE_RANDOM.setSeed(seed);
    }

    /**
     * Generates a cryptographically strong random integer between 0 (inclusive) and the specified bound (exclusive).
     *
     * @param bound The upper bound (exclusive) for the random integer. Must be positive.
     * @return A random integer.
     * @throws IllegalArgumentException if bound is not positive.
     */
    public static int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        return SECURE_RANDOM.nextInt(bound);
    }

    /**
     * Generates a cryptographically strong random integer within a specified range (inclusive).
     *
     * @param min The minimum value (inclusive) of the random integer.
     * @param max The maximum value (inclusive) of the random integer.
     * @return A random integer within the specified range.
     * @throws IllegalArgumentException if min is greater than max.
     */
    public static int nextInt(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("Min cannot be greater than max");
        }
        if (min == max) {
            return min;
        }

        long range = (long) max - min + 1; // Use long to prevent overflow
        if (range > Integer.MAX_VALUE) {
            // Handle large ranges similar to nextInts()
            return (int) (min + (Math.abs(SECURE_RANDOM.nextLong()) % range));
        } else {
            return SECURE_RANDOM.nextInt((int) range) + min;
        }
    }


    /**
     * Generates a cryptographically strong random long value.
     *
     * @return A random long.
     */
    public static long nextLong() {
        return SECURE_RANDOM.nextLong();
    }

    /**
     * Generates a cryptographically strong random long value within a specified range (inclusive).
     *
     * @param min The minimum value (inclusive) of the random long.
     * @param max The maximum value (inclusive) of the random long.
     * @return A random long within the specified range.
     * @throws IllegalArgumentException if min is greater than max.
     */
    public static long nextLong(long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("Min cannot be greater than max");
        }
        if (min == max) return min;

        long range = max - min + 1;
        if (range <= 0) {
            // Handle overflow with rejection sampling
            long result;
            do {
                result = SECURE_RANDOM.nextLong();
            } while (result < min || result > max);
            return result;
        } else {
            // Avoid modulo bias using rejection sampling
            long bits, val;
            do {
                bits = SECURE_RANDOM.nextLong();
                val = (bits < 0 ? ~bits : bits) % range + min; // Absolute value without sign
            } while (val < min || val > max);
            return val;
        }
    }


    /**
     * Fills the specified byte array with cryptographically strong random bytes.
     *
     * @param bytes The byte array to fill with random bytes.
     * @throws NullPointerException if the byte array is null.
     */
    public static void nextBytes(byte[] bytes) {
        SECURE_RANDOM.nextBytes(bytes);
    }

    /**
     * Generates a cryptographically strong random boolean value.
     *
     * @return A random boolean.
     */
    public static boolean nextBoolean() {
        return SECURE_RANDOM.nextBoolean();
    }

    /**
     * Generates a cryptographically strong random double value between 0.0 (inclusive) and 1.0 (exclusive).
     *
     * @return A random double.
     */
    public static double nextDouble() {
        return SECURE_RANDOM.nextDouble();
    }

    /**
     * Generates a cryptographically strong random string of a specified length, using characters from the default set (a-z, A-Z, 0-9).
     *
     * @param length The desired length of the random string. Must be non-negative.
     * @return A random string.
     * @throws IllegalArgumentException if length is negative.
     */
    public static String nextString(int length) {
        return nextString(length, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
    }

    /**
     * Generates a cryptographically strong random string of a specified length, using characters from the provided character set.
     *
     * @param length The desired length of the random string. Must be non-negative.
     * @param chars  The string containing the characters to use for generating the random string.
     * @return A random string composed of characters from the provided set.
     * @throws IllegalArgumentException if length is negative or chars is null or empty.
     */
    public static String nextString(int length, String chars) {
        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative");
        }
        if (chars == null || chars.isEmpty()) {
            throw new IllegalArgumentException("Character set cannot be null or empty");
        }

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Generates a specified quantity of cryptographically strong random integers within a given range.
     *
     * @param count The number of random integers to generate. Must be non-negative.
     * @param min   The minimum value (inclusive) of the random integers.
     * @param max   The maximum value (inclusive) of the random integers.
     * @return An array containing the generated random integers.
     * @throws IllegalArgumentException if count is negative, or if min is greater than max.
     */
    public static int[] nextInts(int count, int min, int max) {
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative");
        }
        if (min > max) {
            throw new IllegalArgumentException("Min cannot be greater than max");
        }

        int[] randomNumbers = new int[count];
        if (count == 0) {
            return randomNumbers;
        }

        // Calculate the range size
        long range = (long) max - min + 1;

        // Handle cases where range is too large for nextInt(int bound)
        if (range > Integer.MAX_VALUE) {
            // For very large ranges, generate a long and then map it to the desired range
            for (int i = 0; i < count; i++) {
                long randomLong = SECURE_RANDOM.nextLong();
                randomNumbers[i] = (int) (min + (Math.abs(randomLong) % range));
            }
        } else {
            // For smaller ranges, use nextInt(int bound)
            for (int i = 0; i < count; i++) {
                randomNumbers[i] = SECURE_RANDOM.nextInt((int) range) + min;
            }
        }
        return randomNumbers;
    }
}
