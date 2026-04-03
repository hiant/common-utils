package io.github.hiant.common.utils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
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

    private static class RenderContext {
        private final Set<Object> visiting = java.util.Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());

        boolean enter(Object value) {
            return visiting.add(value);
        }

        void exit(Object value) {
            visiting.remove(value);
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

        return renderValue(obj, null, obj.getClass(), null, new RenderContext());
    }

    /**
     * Validate that all ENCRYPT-annotated fields on the supplied types can resolve valid AES keys.
     *
     * @param types
     *            classes to validate
     */
    public static void validateEncryptConfiguration(Class<?>... types) {
        if (types == null || types.length == 0) {
            return;
        }
        for (Class<?> type : types) {
            if (type == null) {
                continue;
            }
            List<FieldMeta> fieldMetas = FIELD_META_CACHE.computeIfAbsent(type, ToStringDesensitizeUtils::collectAllFields);
            for (FieldMeta meta : fieldMetas) {
                if (meta.annotation == null || meta.annotation.action() != DesensitizeAction.ENCRYPT) {
                    continue;
                }
                DesensitizeCryptoUtils.requireKey();
                if (meta.annotation.cryptoAlgorithm() == DesensitizeCryptoAlgorithm.AES_CBC) {
                    DesensitizeCryptoUtils.requireAesCbcIv();
                }
            }
        }
    }

    private static String renderObject(Object obj, RenderContext context) {
        if (!context.enter(obj)) {
            return cycleMarker(obj);
        }

        try {
            Class<?> clazz = obj.getClass();
            List<FieldMeta> fieldMetas = FIELD_META_CACHE.computeIfAbsent(clazz,
                ToStringDesensitizeUtils::collectAllFields);

            StringBuilder sb = new StringBuilder(simpleClassName(clazz)).append("(");
            try {
                boolean first = true;
                for (FieldMeta meta : fieldMetas) {
                    if (!first) {
                        sb.append(", ");
                    }
                    first = false;

                    String fieldName = meta.field.getName();
                    Object fieldValue = meta.field.get(obj);
                    String renderedValue = renderValue(fieldValue, meta.annotation, meta.declaringClass, fieldName, context);

                    sb.append(fieldName).append("=").append(renderedValue);
                }
            } catch (IllegalAccessException e) {
                sb.append("toString_desensitize_error=").append(e.getMessage());
            }
            sb.append(")");
            return sb.toString();
        } finally {
            context.exit(obj);
        }
    }

    private static String renderValue(Object value,
                                      Desensitize annotation,
                                      Class<?> declaringClass,
                                      String fieldName,
                                      RenderContext context) {
        if (value == null) {
            return "null";
        }

        if (value instanceof String) {
            if (annotation != null) {
                return processDesensitization((String) value, annotation, declaringClass, fieldName);
            }
            return (String) value;
        }

        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            return renderArray(value, annotation, declaringClass, fieldName, context);
        }
        if (value instanceof Collection) {
            return renderCollection((Collection<?>) value, annotation, declaringClass, fieldName, context);
        }
        if (value instanceof Map) {
            return renderMap((Map<?, ?>) value, annotation, declaringClass, fieldName, context);
        }
        if (isScalarType(valueClass)) {
            return String.valueOf(value);
        }
        return renderObject(value, context);
    }

    private static String renderCollection(Collection<?> values,
                                           Desensitize annotation,
                                           Class<?> declaringClass,
                                           String fieldName,
                                           RenderContext context) {
        if (!context.enter(values)) {
            return cycleMarker(values);
        }

        try {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object value : values) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(renderValue(value, annotation, declaringClass, fieldName, context));
            }
            sb.append("]");
            return sb.toString();
        } finally {
            context.exit(values);
        }
    }

    private static String renderArray(Object array,
                                      Desensitize annotation,
                                      Class<?> declaringClass,
                                      String fieldName,
                                      RenderContext context) {
        if (!context.enter(array)) {
            return cycleMarker(array);
        }

        try {
            StringBuilder sb = new StringBuilder("[");
            int length = Array.getLength(array);
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(renderValue(Array.get(array, i), annotation, declaringClass, fieldName, context));
            }
            sb.append("]");
            return sb.toString();
        } finally {
            context.exit(array);
        }
    }

    private static String renderMap(Map<?, ?> map,
                                    Desensitize annotation,
                                    Class<?> declaringClass,
                                    String fieldName,
                                    RenderContext context) {
        if (!context.enter(map)) {
            return cycleMarker(map);
        }

        try {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(renderValue(entry.getKey(), null, null, null, context));
                sb.append("=");
                sb.append(renderValue(entry.getValue(), annotation, declaringClass, fieldName, context));
            }
            sb.append("}");
            return sb.toString();
        } finally {
            context.exit(map);
        }
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

    private static boolean isScalarType(Class<?> type) {
        if (type.isPrimitive()) {
            return true;
        }
        if (Number.class.isAssignableFrom(type) ||
            Boolean.class == type ||
            Character.class == type ||
            CharSequence.class.isAssignableFrom(type) ||
            Enum.class.isAssignableFrom(type) ||
            Class.class == type) {
            return true;
        }
        Package typePackage = type.getPackage();
        if (typePackage == null) {
            return false;
        }
        String packageName = typePackage.getName();
        return packageName.startsWith("java.") ||
               packageName.startsWith("javax.") ||
               packageName.startsWith("sun.") ||
               packageName.startsWith("com.sun.");
    }

    private static String cycleMarker(Object value) {
        return "<cycle:" + simpleClassName(value.getClass()) + ">";
    }

    private static String simpleClassName(Class<?> type) {
        if (type.isArray()) {
            return simpleClassName(type.getComponentType()) + "[]";
        }
        String simpleName = type.getSimpleName();
        return simpleName.isEmpty() ? type.getName() : simpleName;
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
                if (annotation.cryptoAlgorithm() == DesensitizeCryptoAlgorithm.AES_CBC) {
                    byte[] key = DesensitizeCryptoProviders.getProvider().key();
                    byte[] iv = DesensitizeCryptoProviders.getProvider().iv();
                    return DesensitizeCryptoUtils.encryptForToString(rawValue, key, iv);
                }
                return DesensitizeCryptoUtils.encryptForToString(rawValue);
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
