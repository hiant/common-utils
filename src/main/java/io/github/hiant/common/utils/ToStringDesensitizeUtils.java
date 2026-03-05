package io.github.hiant.common.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Utility class for generating desensitized toString output using reflection.
 * <p>
 * Uses field metadata caching with {@link ConcurrentHashMap} for optimal performance.
 * Fields annotated with {@link Desensitize} will be masked in the output.
 * <p>
 * Supports inheritance: traverses the class hierarchy to include parent class fields.
 * Output format is compatible with Lombok's {@code @ToString} annotation.
 * <p>
 * Thread-safe and compatible with JDK 1.8+.
 *
 * @since JDK1.8
 * @see Desensitize
 * @see DesensitizeType
 * @see Desensitizable
 */
public class ToStringDesensitizeUtils {

    private static final ConcurrentMap<Class<?>, List<FieldMeta>> FIELD_META_CACHE = new ConcurrentHashMap<>();

    private static class FieldMeta {
        final Field       field;
        final Desensitize annotation;
        final Class<?>    declaringClass;

        FieldMeta(Field field, Desensitize annotation, Class<?> declaringClass) {
            this.field = field;
            this.annotation = annotation;
            this.declaringClass = declaringClass;
            this.field.setAccessible(true);
        }
    }

    /**
     * Generate desensitized toString output for an object.
     * <p>
     * Traverses the class hierarchy from parent to child, collecting all instance fields.
     * Fields annotated with {@link Desensitize} will be masked according to their configuration.
     * <p>
     * Output format: {@code ClassName(field1=value1, field2=value2, ...)}
     * This format is compatible with Lombok's {@code @ToString} output.
     *
     * @param obj
     *            the object to generate toString for
     * @return desensitized string representation, or "null" if obj is null
     */
    public static String toDesensitizeString(Object obj) {
        if (obj == null) {
            return "null";
        }

        Class<?> clazz = obj.getClass();
        List<FieldMeta> fieldMetas = FIELD_META_CACHE.computeIfAbsent(clazz,
            ToStringDesensitizeUtils::collectAllFields);

        // Use Lombok-compatible format: ClassName(field=value, ...)
        StringBuilder sb = new StringBuilder(clazz.getSimpleName()).append("(");
        try {
            boolean first = true;
            for (FieldMeta meta : fieldMetas) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;

                String fieldName = meta.field.getName();
                Object fieldValue = meta.field.get(obj);

                if (meta.annotation != null && fieldValue instanceof String) {
                    fieldValue = processDesensitization((String) fieldValue, meta.annotation, meta.declaringClass, fieldName);
                }

                sb.append(fieldName).append("=").append(fieldValue);
            }
        } catch (IllegalAccessException e) {
            sb.append("toString_desensitize_error=").append(e.getMessage());
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Collect all instance fields from the class hierarchy.
     * <p>
     * Traverses from parent class to child class, excluding static and synthetic fields.
     *
     * @param clazz
     *            the class to collect fields from
     * @return list of field metadata ordered from parent to child
     */
    private static List<FieldMeta> collectAllFields(Class<?> clazz) {
        List<FieldMeta> allFields = new ArrayList<>();
        List<Class<?>> hierarchy = new ArrayList<>();

        // Build class hierarchy (from child to parent)
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            hierarchy.add(0, current); // Insert at beginning to reverse order
            current = current.getSuperclass();
        }

        // Collect fields from parent to child
        for (Class<?> c : hierarchy) {
            for (Field field : c.getDeclaredFields()) {
                // Skip static and synthetic fields
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                allFields.add(new FieldMeta(field, field.getAnnotation(Desensitize.class), c));
            }
        }

        return allFields;
    }

    private static String processDesensitization(String rawValue,
                                                 Desensitize annotation,
                                                 Class<?> declaringClass,
                                                 String fieldName) {
        if (rawValue == null || rawValue.isEmpty()) {
            return rawValue;
        }

        DesensitizeAction action = annotation.action();
        if (action == null) {
            action = DesensitizeAction.MASK;
        }

        switch (action) {
            case ENCRYPT:
                // Bind ciphertext to a specific field name (optional but recommended)
                byte[] aad = DesensitizeCryptoUtils.toStringAad(declaringClass, fieldName);
                return DesensitizeCryptoUtils.encryptForToString(rawValue, annotation.keyId(), aad);
            case MASK_WITH_HASH:
                return mask(rawValue, annotation, true);
            case MASK:
            default:
                return mask(rawValue, annotation, annotation.withHash());
        }
    }

    private static String mask(String rawValue, Desensitize annotation, boolean withHash) {
        DesensitizeType type = annotation.type();
        OptionalInt customPrefix = annotation.keepPrefix() != -1 ? OptionalInt.of(annotation.keepPrefix()) : OptionalInt.empty();
        OptionalInt customSuffix = annotation.keepSuffix() != -1 ? OptionalInt.of(annotation.keepSuffix()) : OptionalInt.empty();
        return DesensitizationUtils.strategyDesensitize(rawValue, type, customPrefix, customSuffix, withHash);
    }
}
