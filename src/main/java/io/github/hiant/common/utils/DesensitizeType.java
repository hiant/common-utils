package io.github.hiant.common.utils;

import java.util.OptionalInt;

/**
 * Enumeration of common desensitization types with preset configurations.
 * <p>
 * Each type encapsulates a desensitization strategy with default prefix/suffix settings.
 * Custom prefix/suffix can override the defaults when using {@link Desensitize} annotation.
 *
 * @since JDK1.8
 * @see Desensitize
 * @see DesensitizeStrategy
 */
public enum DesensitizeType {

    /**
     * Default type: no preset prefix/suffix, requires custom configuration.
     */
    DEFAULT((raw, prefix, suffix) -> {
        int p = prefix.orElse(0);
        int s = suffix.orElse(0);
        return DesensitizationUtils.desensitizeForPreset(raw, p, s);
    }, OptionalInt.empty(), OptionalInt.empty()),

    /**
     * Mobile phone number: preset prefix=3, suffix=4.
     * <p>
     * Example: "13812345678" becomes "138****5678"
     */
    MOBILE_PHONE((raw, prefix, suffix) -> {
        int p = prefix.orElse(3);
        int s = suffix.orElse(4);
        return DesensitizationUtils.desensitizeForPreset(raw, p, s);
    }, OptionalInt.of(3), OptionalInt.of(4)),

    /**
     * ID card number: preset prefix=6, suffix=4.
     * <p>
     * Example: "110101199001011234" becomes "110101****1234"
     */
    ID_CARD((raw, prefix, suffix) -> {
        int p = prefix.orElse(6);
        int s = suffix.orElse(4);
        return DesensitizationUtils.desensitizeForPreset(raw, p, s);
    }, OptionalInt.of(6), OptionalInt.of(4)),

    /**
     * Person name: preset prefix=1, suffix=0.
     * <p>
     * Example: "John Doe" becomes "J****"
     */
    NAME((raw, prefix, suffix) -> {
        int p = prefix.orElse(1);
        int s = suffix.orElse(0);
        return DesensitizationUtils.desensitizeForPreset(raw, p, s);
    }, OptionalInt.of(1), OptionalInt.of(0)),

    /**
     * Email address: special strategy, ignores prefix/suffix settings.
     * <p>
     * Masks local part while preserving domain.
     * Example: "john.doe@example.com" becomes "j****@example.com"
     */
    EMAIL((raw, prefix, suffix) -> DesensitizationUtils.desensitizeEmail(raw), OptionalInt.empty(), OptionalInt.empty());

    private final DesensitizeStrategy strategy;
    private final OptionalInt         defaultPrefix;
    private final OptionalInt         defaultSuffix;

    DesensitizeType(DesensitizeStrategy strategy, OptionalInt defaultPrefix, OptionalInt defaultSuffix) {
        this.strategy = strategy;
        this.defaultPrefix = defaultPrefix;
        this.defaultSuffix = defaultSuffix;
    }

    /**
     * Get the desensitization strategy for this type.
     *
     * @return the desensitization strategy
     */
    public DesensitizeStrategy getStrategy() {
        return strategy;
    }

    /**
     * Get the default prefix length for this type.
     *
     * @return the default prefix length, or empty if not preset
     */
    public OptionalInt getDefaultPrefix() {
        return defaultPrefix;
    }

    /**
     * Get the default suffix length for this type.
     *
     * @return the default suffix length, or empty if not preset
     */
    public OptionalInt getDefaultSuffix() {
        return defaultSuffix;
    }
}
