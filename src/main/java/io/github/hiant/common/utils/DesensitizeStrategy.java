package io.github.hiant.common.utils;

import java.util.OptionalInt;

/**
 * Desensitization strategy functional interface.
 * <p>
 * Provides pluggable desensitization logic for different data types.
 * Supports lambda expressions for flexible strategy implementation.
 *
 * @since JDK1.8
 * @see DesensitizeType
 */
@FunctionalInterface
public interface DesensitizeStrategy {

    /**
     * Execute desensitization logic on the input string.
     *
     * @param raw    the original string to be desensitized
     * @param prefix number of prefix characters to retain (empty means use default)
     * @param suffix number of suffix characters to retain (empty means use default)
     * @return the desensitized string
     */
    String mask(String raw, OptionalInt prefix, OptionalInt suffix);
}
