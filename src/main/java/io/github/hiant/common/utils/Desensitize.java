package io.github.hiant.common.utils;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Desensitization annotation for sensitive value rendering.
 * <p>
 * This annotation is primarily used by {@link ToStringDesensitizeUtils#toDesensitizeString(Object)} to generate a
 * Lombok-compatible {@code toString()} output (e.g. {@code ClassName(field=value, ...)}), where selected fields are
 * rendered in a safer form.
 * <p>
 * <b>Masking (default):</b>
 * <pre>
 * {@literal @}Desensitize(type = DesensitizeType.MOBILE_PHONE)
 * {@literal @}Desensitize(type = DesensitizeType.ID_CARD)
 * {@literal @}Override
 * </pre>
 *
 * <b>Masking with custom keep lengths:</b>
 * <pre>
 * {@literal @}Desensitize(type = DesensitizeType.DEFAULT, keepPrefix = 2, keepSuffix = 2)
 * </pre>
 *
 * <b>Masking with hash for internal correlation:</b>
 * <pre>
 * {@literal @}Desensitize(type = DesensitizeType.MOBILE_PHONE, withHash = true)
 * </pre>
 *
 * <b>Reversible encryption (AES-GCM):</b>
 * <pre>
 * {@literal @}Desensitize(action = DesensitizeAction.ENCRYPT, keyId = "default")
 * </pre>
 *
 * <h3>Key provisioning</h3>
 * This library is dependency-free and does not assume any DI container.
 * <p>
 * You can provide keys via system properties:
 * <ul>
 *   <li>{@code -Ddesensitize.crypto.defaultKeyId=default}</li>
 *   <li>{@code -Ddesensitize.crypto.key.default=<Base64(AES key bytes)>}</li>
 * </ul>
 * Or inject a custom provider at runtime via {@link DesensitizeCryptoProviders#setProvider(DesensitizeCryptoProvider)}
 * (e.g. a Spring adapter that reads keys from a configuration center).
 *
 * @since JDK1.8
 * @see DesensitizeType
 * @see ToStringDesensitizeUtils
 * @see Desensitizable
 */
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Desensitize {

    /**
     * Rendering action.
     * <p>
     * When {@link DesensitizeAction#MASK} or {@link DesensitizeAction#MASK_WITH_HASH} is used, the value is
     * desensitized via {@link DesensitizeType} and related parameters.
     * <p>
     * When {@link DesensitizeAction#ENCRYPT} is used, masking parameters are ignored and the value is rendered as
     * reversible ciphertext using AES-GCM.
     *
     * @return action, defaults to {@link DesensitizeAction#MASK}
     */
    DesensitizeAction action() default DesensitizeAction.MASK;

    /**
     * Key id used for encryption when {@link #action()} is {@link DesensitizeAction#ENCRYPT}.
     * <p>
     * If blank, the effective key id is provided by {@link DesensitizeCryptoProvider#defaultKeyId()}.
     *
     * @return key id (optional)
     */
    String keyId() default "";

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
