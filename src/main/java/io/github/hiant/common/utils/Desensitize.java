package io.github.hiant.common.utils;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Desensitization annotation for field-level data masking.
 * <p>
 * Used with {@link ToStringDesensitizeUtils#toDesensitizeString(Object)} to generate
 * desensitized toString output for objects containing sensitive data.
 *
 * @since JDK1.8
 * @see DesensitizeType
 * @see ToStringDesensitizeUtils
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Desensitize {

    /**
     * Desensitization type with preset prefix/suffix configuration.
     *
     * @return the desensitization type, defaults to {@link DesensitizeType#DEFAULT}
     */
    DesensitizeType type() default DesensitizeType.DEFAULT;

    /**
     * Number of prefix characters to keep visible.
     * <p>
     * Value of -1 means use the preset value from {@link DesensitizeType}.
     *
     * @return prefix length to retain, defaults to -1 (use preset)
     */
    int keepPrefix() default -1;

    /**
     * Number of suffix characters to keep visible.
     * <p>
     * Value of -1 means use the preset value from {@link DesensitizeType}.
     *
     * @return suffix length to retain, defaults to -1 (use preset)
     */
    int keepSuffix() default -1;

    /**
     * Whether to append hash for internal tracing.
     * <p>
     * When enabled, appends MD5 hash of original value for correlation purposes.
     *
     * @return true to append hash, defaults to false
     */
    boolean withHash() default false;
}
