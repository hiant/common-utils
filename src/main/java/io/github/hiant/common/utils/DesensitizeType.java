package io.github.hiant.common.utils;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
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
@Getter
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
    }, OptionalInt.of(3), OptionalInt.of(4), "MSISDN", "PHONE_NO"),

    /**
     * Bank card number: preset prefix=6, suffix=4.
     * <p>
     * Example: "6222021234567890" becomes "622202****7890"
     */
    BANK_CARD((raw, prefix, suffix) -> {
        int p = prefix.orElse(6);
        int s = suffix.orElse(4);
        return DesensitizationUtils.desensitizeForPreset(raw, p, s);
    }, OptionalInt.of(6), OptionalInt.of(4)),

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

    private static final String                       SYS_PROP_MASK_RATIO_PREFIX = "desensitize.maskRatio.";

    private static final Map<String, DesensitizeType> NAME_LOOKUP                = new HashMap<>();

    static {
        for (DesensitizeType type : values()) {
            registerLookupKey(type.name(), type);
            for (String alias : type.aliases) {
                registerLookupKey(alias, type);
            }
        }
    }

    private final DesensitizeStrategy strategy;
    private final OptionalInt         defaultPrefix;
    private final OptionalInt         defaultSuffix;
    private final String[]            aliases;

    DesensitizeType(DesensitizeStrategy strategy,
                    OptionalInt defaultPrefix,
                    OptionalInt defaultSuffix,
                    String... aliases) {
        this.strategy = strategy;
        this.defaultPrefix = defaultPrefix;
        this.defaultSuffix = defaultSuffix;
        this.aliases = aliases == null ? new String[0] : aliases.clone();
    }

    /**
     * Type-level configured required mask ratio.
     * Returns empty when no system property is configured (meaning: do not validate mask ratio).
     *
     * @return configured mask ratio, or empty if not configured/invalid
     */
    public OptionalDouble getConfiguredMaskRatio() {
        String keyLower = SYS_PROP_MASK_RATIO_PREFIX + name().toLowerCase();
        String keyUpper = SYS_PROP_MASK_RATIO_PREFIX + name();
        String value = System.getProperty(keyLower);
        if (value == null) {
            value = System.getProperty(keyUpper);
        }
        if (value == null || value.trim().isEmpty()) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(sanitizeMaskRatio(Double.parseDouble(value.trim())));
        } catch (NumberFormatException ignored) {
            return OptionalDouble.empty();
        }
    }

    public static DesensitizeType fromName(String nameOrAlias) {
        if (nameOrAlias == null) {
            throw new IllegalArgumentException("typeName cannot be null");
        }
        String normalized = normalizeLookupKey(nameOrAlias);
        DesensitizeType type = NAME_LOOKUP.get(normalized);
        if (type == null) {
            throw new IllegalArgumentException("Unknown desensitize type: " + nameOrAlias);
        }
        return type;
    }

    private static void registerLookupKey(String key, DesensitizeType type) {
        if (key == null) {
            return;
        }
        String normalized = normalizeLookupKey(key);
        if (!normalized.isEmpty()) {
            NAME_LOOKUP.putIfAbsent(normalized, type);
        }
    }

    private static String normalizeLookupKey(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }

        StringBuilder normalized = new StringBuilder(s.length());
        char prev = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '-' || c == ' ') {
                c = '_';
            }

            if (c == '_') {
                if (normalized.length() == 0 || normalized.charAt(normalized.length() - 1) == '_') {
                    prev = c;
                    continue;
                }
                normalized.append('_');
                prev = c;
                continue;
            }

            if (Character.isUpperCase(c) && normalized.length() > 0 && (Character.isLowerCase(prev) || Character.isDigit(prev))) {
                normalized.append('_');
            }

            normalized.append(Character.toUpperCase(c));
            prev = c;
        }

        int len = normalized.length();
        if (len > 0 && normalized.charAt(len - 1) == '_') {
            normalized.setLength(len - 1);
        }
        return normalized.toString();
    }

    private static double sanitizeMaskRatio(double ratio) {
        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return 0.0;
        }
        if (ratio < 0.0) {
            return 0.0;
        }
        if (ratio > 1.0) {
            return 1.0;
        }
        return ratio;
    }
}
