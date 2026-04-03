package io.github.hiant.common.utils;

/**
 * Global access point for {@link DesensitizeCryptoProvider}.
 * <p>
 * This library is dependency-free. Host applications can inject a provider at runtime (e.g. reading from a
 * configuration center) by calling {@link #setProvider(DesensitizeCryptoProvider)}.
 * <p>
 * When no provider is injected, a system-property and environment-variable based provider is used.
 *
 * @since JDK1.8
 */
public final class DesensitizeCryptoProviders {

    private static volatile DesensitizeCryptoProvider provider;

    private DesensitizeCryptoProviders() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Inject a provider.
     * <p>
     * This method is safe to call multiple times; the provider is replaced atomically.
     *
     * @param p
     *            provider (null resets to the default provider)
     */
    public static void setProvider(DesensitizeCryptoProvider p) {
        provider = p;
    }

    /**
     * Inject a fixed config.
     *
     * @param config
     *            immutable config (null resets to the default provider)
     */
    public static void setConfig(DesensitizeCryptoConfig config) {
        provider = config;
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
        throw new IllegalStateException();
    }

    static boolean isSupportedAesKeyLength(byte[] key) {
        return key != null && (key.length == 16 || key.length == 24 || key.length == 32);
    }

}
