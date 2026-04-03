package io.github.hiant.common.utils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Immutable programmatic configuration for {@link DesensitizeAction#ENCRYPT}.
 * <p>
 * This is a convenience implementation of {@link DesensitizeCryptoProvider} for applications that want to
 * configure AES keys directly without implementing the provider interface.
 *
 * @since JDK1.8
 */
public final class DesensitizeCryptoConfig implements DesensitizeCryptoProvider {

    private final byte[] key;
    private final byte[] iv;

    private DesensitizeCryptoConfig(byte[] key, byte[] iv) {
        this.key = key;
        this.iv = iv;
    }

    public static DesensitizeCryptoConfig of(byte[] key) {
        return of(key, null);
    }

    public static DesensitizeCryptoConfig of(byte[] key, byte[] iv) {
        return new DesensitizeCryptoConfig(key, iv);
    }

    public static DesensitizeCryptoConfig of(String key) {
        return of(key, null);
    }

    public static DesensitizeCryptoConfig of(String key, String iv) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (iv == null || iv.trim().isEmpty()) {
            throw new IllegalArgumentException("iv must not be blank");
        }
        return of(key.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public byte[] key() {
        return key == null ? null : Arrays.copyOf(key, key.length);
    }

    @Override
    public byte[] iv() {
        return iv == null ? null : Arrays.copyOf(iv, iv.length);
    }
}
