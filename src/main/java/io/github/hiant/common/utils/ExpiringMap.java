package io.github.hiant.common.utils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Optimized expiring map with enhanced cleanup logic and resource management.
 * Handles duplicate expiration keys and ensures thorough resource release on close.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
@Slf4j
public class ExpiringMap<K, V> implements Map<K, V>, AutoCloseable {

    // Default time-to-live in seconds
    public static final int DEFAULT_TIME_TO_LIVE = 60;
    // Default expiration check interval in seconds
    public static final int DEFAULT_EXPIRATION_INTERVAL = 1;

    private final ConcurrentHashMap<K, ExpiringEntry<V>> dataMap;
    private final DelayQueue<ExpiringKey<K>> delayQueue;
    private final CopyOnWriteArrayList<ListenerWrapper> listeners;
    private final ScheduledExecutorService scheduler;
    private final ReadWriteLock stateLock;
    private final AtomicLong sequenceGenerator;
    private volatile boolean closed;
    private final long timeToLiveNanos;

    /**
     * Constructs an ExpiringMap with default TTL and expiration interval.
     */
    public ExpiringMap() {
        this(DEFAULT_TIME_TO_LIVE, DEFAULT_EXPIRATION_INTERVAL);
    }

    /**
     * Constructs an ExpiringMap with specified TTL and default expiration interval.
     *
     * @param timeToLive Time-to-live in seconds
     */
    public ExpiringMap(int timeToLive) {
        this(timeToLive, DEFAULT_EXPIRATION_INTERVAL);
    }

    /**
     * Constructs an ExpiringMap with specified TTL and expiration interval.
     *
     * @param timeToLive         Time-to-live in seconds
     * @param expirationInterval Expiration check interval in seconds
     */
    public ExpiringMap(int timeToLive, int expirationInterval) {
        this.timeToLiveNanos = TimeUnit.SECONDS.toNanos(timeToLive);
        this.dataMap = new ConcurrentHashMap<>();
        this.delayQueue = new DelayQueue<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.stateLock = new ReentrantReadWriteLock();
        this.sequenceGenerator = new AtomicLong(0);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ExpiringMap-CleanupScheduler");
            t.setDaemon(true);
            return t;
        });

        scheduleCleanup(expirationInterval);
    }

    private void scheduleCleanup(int intervalSeconds) {
        long initialDelay = intervalSeconds / 2L;
        this.scheduler.scheduleAtFixedRate(this::cleanUpExpiredEntries, initialDelay, intervalSeconds, TimeUnit.SECONDS);
    }

    // ----------------------------
    // Map Interface Implementation
    // ----------------------------

    @Override
    public int size() {
        cleanUpIfNecessary();
        return dataMap.size();
    }

    @Override
    public boolean isEmpty() {
        cleanUpIfNecessary();
        return dataMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        cleanUpIfNecessary();
        return dataMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        cleanUpIfNecessary();
        for (ExpiringEntry<V> entry : dataMap.values()) {
            if (!entry.isExpired() && Objects.equals(entry.getValue(), value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V get(Object key) {
        cleanUpIfNecessary();
        ExpiringEntry<V> entry = dataMap.get(key);
        return entry != null && !entry.isExpired() ? entry.getValue() : null;
    }

    @Override
    public V put(K key, V value) {
        checkClosed();
        if (value == null) throw new IllegalArgumentException("Value cannot be null");

        long expirationNanos = System.nanoTime() + timeToLiveNanos;
        ExpiringEntry<V> oldEntry = dataMap.put(key, new ExpiringEntry<>(value, expirationNanos));
        delayQueue.put(new ExpiringKey<>(key, expirationNanos, sequenceGenerator.incrementAndGet()));

        return oldEntry != null ? oldEntry.getValue() : null;
    }

    @Override
    public V remove(Object key) {
        checkClosed();
        cleanUpIfNecessary();
        ExpiringEntry<V> entry = dataMap.remove(key);
        if (entry != null) notifyExpiration((K) key, entry.getValue(), false);
        return entry != null ? entry.getValue() : null;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        checkClosed();
        if (m == null || m.isEmpty()) return;

        long expirationNanos = System.nanoTime() + timeToLiveNanos;
        List<ExpiringKey<K>> keys = new ArrayList<>(m.size());
        m.forEach((k, v) -> {
            dataMap.put(k, new ExpiringEntry<>(v, expirationNanos));
            keys.add(new ExpiringKey<>(k, expirationNanos, sequenceGenerator.incrementAndGet()));
        });
        delayQueue.addAll(keys);
    }

    @Override
    public void clear() {
        checkClosed();
        dataMap.clear();
        delayQueue.clear();
    }

    @Override
    public Set<K> keySet() {
        cleanUpIfNecessary();
        return dataMap.keySet();
    }

    @Override
    public Collection<V> values() {
        cleanUpIfNecessary();
        List<V> result = new ArrayList<>();
        for (ExpiringEntry<V> entry : dataMap.values()) {
            if (!entry.isExpired()) result.add(entry.getValue());
        }
        return result;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        cleanUpIfNecessary();
        Set<Entry<K, V>> result = new HashSet<>();
        for (Entry<K, ExpiringEntry<V>> entry : dataMap.entrySet()) {
            if (!entry.getValue().isExpired()) {
                result.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().getValue()));
            }
        }
        return result;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        checkClosed();
        if (value == null) throw new IllegalArgumentException("Value cannot be null");

        cleanUpIfNecessary();

        ExpiringEntry<V> currentEntry = dataMap.get(key);
        if (currentEntry != null && !currentEntry.isExpired()) {
            return currentEntry.getValue();
        }

        long expirationNanos = System.nanoTime() + timeToLiveNanos;
        ExpiringEntry<V> newEntry = new ExpiringEntry<>(value, expirationNanos);
        ExpiringEntry<V> oldEntry = dataMap.putIfAbsent(key, newEntry);

        if (oldEntry == null) {
            delayQueue.put(new ExpiringKey<>(key, expirationNanos, sequenceGenerator.incrementAndGet()));
        }

        return oldEntry != null ? oldEntry.getValue() : null;
    }

    @Override
    public boolean remove(Object key, Object value) {
        checkClosed();
        cleanUpIfNecessary();

        ExpiringEntry<V> entry = dataMap.get(key);
        if (entry != null && Objects.equals(entry.getValue(), value) && !entry.isExpired()) {
            boolean removed = dataMap.remove(key, entry);
            if (removed) notifyExpiration((K) key, entry.getValue(), false);
            return removed;
        }
        return false;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        checkClosed();
        if (newValue == null) throw new IllegalArgumentException("Value cannot be null");

        cleanUpIfNecessary();

        ExpiringEntry<V> currentEntry = dataMap.get(key);
        if (currentEntry != null && !currentEntry.isExpired() && Objects.equals(currentEntry.getValue(), oldValue)) {
            long expirationNanos = System.nanoTime() + timeToLiveNanos;
            ExpiringEntry<V> newEntry = new ExpiringEntry<>(newValue, expirationNanos);
            if (dataMap.replace(key, currentEntry, newEntry)) {
                delayQueue.put(new ExpiringKey<>(key, expirationNanos, sequenceGenerator.incrementAndGet()));
                return true;
            }
        }
        return false;
    }

    @Override
    public V replace(K key, V value) {
        checkClosed();
        if (value == null) throw new IllegalArgumentException("Value cannot be null");

        cleanUpIfNecessary();

        ExpiringEntry<V> currentEntry = dataMap.get(key);
        if (currentEntry != null && !currentEntry.isExpired()) {
            long expirationNanos = System.nanoTime() + timeToLiveNanos;
            ExpiringEntry<V> newEntry = new ExpiringEntry<>(value, expirationNanos);
            if (dataMap.replace(key, currentEntry, newEntry)) {
                delayQueue.put(new ExpiringKey<>(key, expirationNanos, sequenceGenerator.incrementAndGet()));
                notifyExpiration(key, currentEntry.getValue(), false);
                return currentEntry.getValue();
            }
        }
        return null;
    }

    // ----------------------------
    // Additional Methods
    // ----------------------------

    public void addExpirationListener(Consumer<ExpirationEvent<K, V>> listener) {
        checkClosed();
        if (listener != null) listeners.add(new ListenerWrapper(listener));
    }

    public boolean removeExpirationListener(Consumer<ExpirationEvent<K, V>> listener) {
        return listeners.removeIf(wrapper -> wrapper.get() == listener);
    }

    private void notifyExpiration(K key, V value, boolean isExpired) {
        for (ListenerWrapper wrapper : listeners) {
            Consumer<ExpirationEvent<K, V>> listener = wrapper.get();
            if (listener != null) {
                try {
                    listener.accept(new ExpirationEvent<>(key, value, isExpired));
                } catch (Exception e) {
                    log.error("Listener execution error", e);
                }
            }
        }
    }

    private void checkClosed() {
        if (closed) throw new IllegalStateException("Map is closed");
    }

    @Override
    public void close() {
        stateLock.writeLock().lock();
        try {
            if (!closed) {
                closed = true;
                scheduler.shutdownNow();

                ExpiringKey<K> key;
                while ((key = delayQueue.poll()) != null) {
                    dataMap.remove(key.getKey());
                }

                dataMap.clear();
                listeners.clear();
                log.info("ExpiringMap closed, all resources released");
            }
        } catch (Exception e) {
            log.error("Close operation error", e);
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    // ----------------------------
    // Internal Classes
    // ----------------------------

    private void cleanUpExpiredEntries() {
        stateLock.readLock().lock();
        try {
            if (closed) return;

            ExpiringKey<K> expiredKey;
            while ((expiredKey = delayQueue.poll()) != null) {
                ExpiringEntry<V> entry = dataMap.get(expiredKey.getKey());
                if (entry != null && entry.expirationNanos == expiredKey.expirationNanos) {
                    if (entry.isExpired()) {
                        dataMap.remove(expiredKey.getKey(), entry);
                        notifyExpiration(expiredKey.getKey(), entry.getValue(), true);
                    }
                }
            }

            dataMap.forEach((key, entry) -> {
                if (entry.isExpired()) {
                    dataMap.remove(key, entry);
                    notifyExpiration(key, entry.getValue(), true);
                }
            });
        } catch (Exception e) {
            log.error("Cleanup error", e);
        } finally {
            stateLock.readLock().unlock();
        }
    }

    private void cleanUpIfNecessary() {
        if (ThreadLocalRandom.current().nextInt(100) < 5) {
            cleanUpExpiredEntries();
        }
    }

    @Getter
    private static class ExpiringEntry<V> {
        final V value;
        final long expirationNanos;

        ExpiringEntry(V value, long expirationNanos) {
            this.value = value;
            this.expirationNanos = expirationNanos;
        }

        boolean isExpired() {
            return System.nanoTime() >= expirationNanos;
        }

        V getValue() {
            return value;
        }
    }

    @Getter
    private static class ExpiringKey<K> implements Delayed {
        final K key;
        final long expirationNanos;
        final long sequenceNumber;

        ExpiringKey(K key, long expirationNanos, long sequenceNumber) {
            this.key = key;
            this.expirationNanos = expirationNanos;
            this.sequenceNumber = sequenceNumber;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(expirationNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            if (other == this) return 0;
            if (other instanceof ExpiringKey) {
                ExpiringKey<?> o = (ExpiringKey<?>) other;
                long diff = expirationNanos - o.expirationNanos;
                if (diff != 0) return Long.signum(diff);
                return Long.signum(sequenceNumber - o.sequenceNumber);
            }
            long diff = getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS);
            return Long.signum(diff);
        }
    }

    public static class ExpirationEvent<K, V> {
        public final K key;
        public final V value;
        public final boolean isExpired;

        public ExpirationEvent(K key, V value, boolean isExpired) {
            this.key = key;
            this.value = value;
            this.isExpired = isExpired;
        }
    }

    private static class ListenerWrapper<K, V> {
        private final WeakReference<Consumer<ExpirationEvent<K, V>>> ref;

        ListenerWrapper(Consumer<ExpirationEvent<K, V>> listener) {
            this.ref = new WeakReference<>(listener);
        }

        public Consumer<ExpirationEvent<K, V>> get() {
            return ref.get();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ListenerWrapper)) return false;
            Consumer<ExpirationEvent<K, V>> other = ((ListenerWrapper<K, V>) obj).get();
            return other != null && other.equals(get());
        }
    }
}
