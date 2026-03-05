package io.github.hiant.common.utils;

import java.util.Base64;

/**
 * Global access point for {@link DesensitizeCryptoProvider}.
 * <p>
 * This library is dependency-free. Host applications can inject a provider at runtime (e.g. reading from a
 * configuration center) by calling {@link #setProvider(DesensitizeCryptoProvider)}.
 * <p>
 * When no provider is injected, a system-property based provider is used.
 *
 * <h3>System properties</h3>
 * <ul>
 *   <li>{@code desensitize.crypto.defaultKeyId}: default key id (fallback to {@code default})</li>
 *   <li>{@code desensitize.crypto.key.<keyId>}: Base64 encoded raw AES key bytes (16/24/32 bytes)</li>
 * </ul>
 *
 * @since JDK1.8
 */
public final class DesensitizeCryptoProviders {

    private static final String SYS_PROP_DEFAULT_KEY_ID = "desensitize.crypto.defaultKeyId";
    private static final String SYS_PROP_KEY_PREFIX     = "desensitize.crypto.key.";

    private static volatile DesensitizeCryptoProvider provider;

    private DesensitizeCryptoProviders() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Inject a provider.
     * <p>
     * This method is safe to call multiple times; the provider is replaced atomically.
     *
     * @param p provider (null resets to the default system-property provider)
     */
    public static void setProvider(DesensitizeCryptoProvider p) {
        provider = p;
    }

    /**
     * Get the effective provider.
     *
     * @return provider (never null)
     */
    public static DesensitizeCryptoProvider getProvider() {
        DesensitizeCryptoProvider p = provider;
        if (p != null) {
            return p;
        }
        return SystemPropertyCryptoProvider.INSTANCE;
    }

    private enum SystemPropertyCryptoProvider implements DesensitizeCryptoProvider {
        INSTANCE;

        @Override
        public String defaultKeyId() {
            String v = System.getProperty(SYS_PROP_DEFAULT_KEY_ID);
            if (v == null || v.trim().isEmpty()) {
                return "default";
            }
            return v.trim();
        }

        @Override
        public byte[] findAesKey(String keyId) {
            if (keyId == null || keyId.trim().isEmpty()) {
                return null;
            }
            String prop = System.getProperty(SYS_PROP_KEY_PREFIX + keyId.trim());
            if (prop == null || prop.trim().isEmpty()) {
                return null;
            }
            try {
                return Base64.getDecoder().decode(prop.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}
