package io.github.hiant.common.utils;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Utility class for converting between decimal numbers and an arbitrary radix
 * defined by a user-supplied character set.
 * <p>
 * Thread-safe, zero-dependency, JDK 8+.
 */
public final class RadixUtils {

    /* ========================== Built-in Charsets ========================== */

    /**
     * 26 symbols: A-Z
     */
    private static final char[] ALPHABET26_ARR =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    /**
     * 36 symbols: 0-9 A-Z
     */
    private static final char[] BASE36_ARR =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    /**
     * 34 symbols: 0-9 A-Z except AMBIGUOUS chars (O,I)
     */
    private static final char[] BASE34_ARR =
            "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    /**
     * 32 symbols: 2-9 A-Z except AMBIGUOUS chars (0,O,1,I)
     */
    private static final char[] BASE32_ARR =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    /**
     * Return an immutable copy of the desired charset.
     */
    public static char[] alphabet() {
        return ALPHABET26_ARR.clone();
    }

    public static char[] base36() {
        return BASE36_ARR.clone();
    }

    public static char[] base34() {
        return BASE34_ARR.clone();
    }

    public static char[] base32() {
        return BASE32_ARR.clone();
    }

    /* ========================== Entry Points ========================== */

    public static EncodeStep encode(long value) {
        return new EncodeStep(value);
    }

    public static EncodeStep encode(BigInteger value) {
        return new EncodeStep(value);
    }

    public static DecodeStep decode(String str) {
        return new DecodeStep(str);
    }

    /* ========================== Internal Codec ========================== */

    private static final class RadixCodec {
        final int radix;
        final char[] symbols;
        final int[] lookup;

        RadixCodec(char[] symbols, boolean caseInsensitive) {
            this.symbols = Arrays.copyOf(symbols, symbols.length);
            this.radix = this.symbols.length;

            int max = 0;
            for (char c : this.symbols) {
                max = Math.max(max, c);
            }
            lookup = new int[max + 1];
            Arrays.fill(lookup, -1);

            for (int i = 0; i < this.symbols.length; i++) {
                char c = this.symbols[i];
                lookup[c] = i;
                if (caseInsensitive) {
                    char lower = Character.toLowerCase(c);
                    if (lower != c) {
                        lookup[lower] = i;
                    }

                    char upper = Character.toUpperCase(c);
                    if (upper != c) {
                        lookup[upper] = i;
                    }
                }
            }
        }

        /* ---------- Encoding ---------- */

        String encode(long value) {
            if (value < 0) {
                throw new IllegalArgumentException("Negative value");
            }
            if (value == 0) {
                return String.valueOf(symbols[0]);
            }

            int len = 0;
            long tmp = value;
            do {
                len++;
                tmp /= radix;
            } while (tmp != 0);

            char[] buf = new char[len];
            int pos = len;
            while (value != 0) {
                buf[--pos] = symbols[(int) (value % radix)];
                value /= radix;
            }
            return new String(buf);
        }

        String encode(BigInteger value) {
            if (value.signum() < 0) {
                throw new IllegalArgumentException("Negative value");
            }
            if (value.equals(BigInteger.ZERO)) {
                return String.valueOf(symbols[0]);
            }

            BigInteger base = BigInteger.valueOf(radix);
            StringBuilder sb = new StringBuilder();
            BigInteger v = value;
            while (v.signum() > 0) {
                BigInteger[] qr = v.divideAndRemainder(base);
                sb.append(symbols[qr[1].intValue()]);
                v = qr[0];
            }
            return sb.reverse().toString();
        }

        /* ---------- Decoding ---------- */

        long decode(String str) {
            if (str == null) {
                throw new IllegalArgumentException("Null input");
            }
            long val = 0;
            for (char c : str.toCharArray()) {
                int v = c < lookup.length ? lookup[c] : -1;
                if (v < 0) {
                    throw new IllegalArgumentException("Illegal char: " + c);
                }
                if (val > (Long.MAX_VALUE - v) / radix) {
                    throw new ArithmeticException("Overflow");
                }
                val = val * radix + v;
            }
            return val;
        }

        BigInteger decodeBig(String str) {
            if (str == null) {
                throw new IllegalArgumentException("Null input");
            }
            BigInteger val = BigInteger.ZERO;
            BigInteger base = BigInteger.valueOf(radix);
            for (char c : str.toCharArray()) {
                int v = c < lookup.length ? lookup[c] : -1;
                if (v < 0) {
                    throw new IllegalArgumentException("Illegal char: " + c);
                }
                val = val.multiply(base).add(BigInteger.valueOf(v));
            }
            return val;
        }
    }

    /* ========================== Zero-Dependency LRU Cache ========================== */

    private static final int CACHE_MAX = 64;
    private static final ConcurrentHashMap<Key, RadixCodec> CACHE = new ConcurrentHashMap<>();
    private static final Deque<Key> LRU = new ArrayDeque<>();
    private static final ReentrantLock LOCK = new ReentrantLock();

    private static RadixCodec codec(char[] symbols, boolean caseInsensitive) {
        Key key = new Key(symbols, caseInsensitive);
        RadixCodec codec = CACHE.get(key);
        if (codec != null) {
            LOCK.lock();
            try {
                LRU.remove(key);
                LRU.addLast(key);
            } finally {
                LOCK.unlock();
            }
            return codec;
        }

        LOCK.lock();
        try {
            codec = CACHE.computeIfAbsent(key, k -> new RadixCodec(k.symbols, k.caseInsensitive));
            LRU.remove(key);
            LRU.addLast(key);
            if (LRU.size() > CACHE_MAX) {
                CACHE.remove(LRU.pollFirst());
            }
            return codec;
        } finally {
            LOCK.unlock();
        }
    }

    private static final class Key {
        final String symbolsStr;
        final char[] symbols;
        final boolean caseInsensitive;

        Key(char[] symbols, boolean caseInsensitive) {
            this.symbols = Arrays.copyOf(symbols, symbols.length);
            this.symbolsStr = new String(symbols);
            this.caseInsensitive = caseInsensitive;
        }

        @Override
        public int hashCode() {
            return symbolsStr.hashCode() * 31 + Boolean.hashCode(caseInsensitive);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key && caseInsensitive == ((Key) o).caseInsensitive && symbolsStr.equals(((Key) o).symbolsStr);
        }
    }

    /* ========================== Fluent DSL ========================== */

    public static final class EncodeStep {
        private final long longVal;
        private final BigInteger bigVal;
        private final boolean isBig;

        EncodeStep(long value) {
            longVal = value;
            bigVal = null;
            isBig = false;
        }

        EncodeStep(BigInteger v) {
            bigVal = Objects.requireNonNull(v);
            longVal = 0;
            isBig = true;
        }

        public String with(char[] symbols) {
            return with(symbols, false);
        }

        public String with(char[] symbols, boolean caseInsensitive) {
            RadixCodec c = codec(symbols, caseInsensitive);
            return isBig ? c.encode(bigVal) : c.encode(longVal);
        }


        public String withAlphabet() {
            return withAlphabet(true);
        }

        public String withBase36() {
            return withBase36(true);
        }

        public String withBase34() {
            return withBase34(true);
        }

        public String withBase32() {
            return withBase32(true);
        }

        public String withAlphabet(boolean caseInsensitive) {
            return with(ALPHABET26_ARR, caseInsensitive);
        }

        public String withBase36(boolean caseInsensitive) {
            return with(BASE36_ARR, caseInsensitive);
        }

        public String withBase34(boolean caseInsensitive) {
            return with(BASE34_ARR, caseInsensitive);
        }

        public String withBase32(boolean caseInsensitive) {
            return with(BASE32_ARR, caseInsensitive);
        }
    }

    public static final class DecodeStep {
        private final String str;

        DecodeStep(String s) {
            str = Objects.requireNonNull(s);
        }

        public long with(char[] symbols) {
            return with(symbols, false);
        }

        public long with(char[] symbols, boolean ci) {
            return codec(symbols, ci).decode(str);
        }

        public long withAlphabet() {
            return withAlphabet(true);
        }

        public long withBase36() {
            return withBase36(true);
        }

        public long withBase34() {
            return withBase34(true);
        }

        public long withBase32() {
            return withBase32(true);
        }

        public long withAlphabet(boolean caseInsensitive) {
            return codec(ALPHABET26_ARR, caseInsensitive).decode(str);
        }

        public long withBase36(boolean caseInsensitive) {
            return codec(BASE36_ARR, caseInsensitive).decode(str);
        }

        public long withBase34(boolean caseInsensitive) {
            return codec(BASE34_ARR, caseInsensitive).decode(str);
        }

        public long withBase32(boolean caseInsensitive) {
            return codec(BASE32_ARR, caseInsensitive).decode(str);
        }

    }

    /* ========================== Shortcuts ========================== */

    public static String toAlphabet(long v) {
        return encode(v).withAlphabet();
    }

    public static String toBase36(long v) {
        return encode(v).withBase36();
    }

    public static String toBase34(long v) {
        return encode(v).withBase34();
    }


    public static String toBase32(long v) {
        return encode(v).withBase32();
    }

    public static long fromAlphabet(String s) {
        return decode(s).withAlphabet();
    }

    public static long fromBase36(String s) {
        return decode(s).withBase36();
    }

    public static long fromBase34(String s) {
        return decode(s).withBase34();
    }

    public static long fromBase32(String s) {
        return decode(s).withBase32();
    }

    private RadixUtils() {
    }
}
