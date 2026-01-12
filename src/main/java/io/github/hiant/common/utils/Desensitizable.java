package io.github.hiant.common.utils;

/**
 * Interface for objects that support desensitized string representation.
 * <p>
 * Provides a default implementation for generating desensitized toString output.
 * Classes implementing this interface can call {@link #toDesensitizedString()}
 * in their {@code toString()} override.
 * <p>
 * <b>Usage:</b>
 * <pre>{@code
 * public class UserInfo implements Desensitizable {
 *
 *     @Desensitize(type = DesensitizeType.MOBILE_PHONE)
 *     private String phone;
 *
 *     @Desensitize(type = DesensitizeType.ID_CARD)
 *     private String idCard;
 *
 *     @Override
 *     public String toString() {
 *         return toDesensitizedString();
 *     }
 * }
 * }</pre>
 * <p>
 * <b>Inheritance Support:</b>
 * <p>
 * The desensitized output includes all fields from the class hierarchy,
 * ordered from parent to child. Both parent and child class fields
 * can use {@link Desensitize} annotations.
 *
 * @since JDK1.8
 * @see Desensitize
 * @see DesensitizeType
 * @see ToStringDesensitizeUtils
 */
public interface Desensitizable {

    /**
     * Generate a desensitized string representation of this object.
     * <p>
     * Fields annotated with {@link Desensitize} will be masked according to their configuration.
     * Output format is compatible with Lombok's {@code @ToString}: {@code ClassName(field=value, ...)}
     *
     * @return desensitized string representation
     */
    default String toDesensitizedString() {
        return ToStringDesensitizeUtils.toDesensitizeString(this);
    }
}
