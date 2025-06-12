package io.github.hiant.common.utils;

import lombok.Getter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Distributed unique ID generator using Snowflake algorithm.
 * <p>
 * The ID is a 64-bit long value composed of:
 * - Timestamp (41 bits)
 * - Datacenter ID (5 bits)
 * - Worker ID (5 bits)
 * - Sequence number (12 bits)
 * </p>
 */
public class SnowflakeIdUtils {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MMddHHmmss")
            .withZone(ZoneId.systemDefault());
    public static final long DEFAULT_WORKER_ID = 1L;
    public static final long DEFAULT_DATACENTER_ID = 1L;

    private static final long TWEPOCH = 1577836800000L;                                     // Start timestamp: 2020-01-01

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);                            // Mask to isolate sequence bits

    @Getter
    private final long workerId;
    @Getter
    private final long datacenterId;
    private final AtomicLong sequence = new AtomicLong(0L);
    private volatile long lastTimestamp = -1L;


    private static volatile SnowflakeIdUtils instance = null;

    /**
     * Constructs with specified node IDs.
     *
     * @param workerId     the worker ID (must be within 0~31)
     * @param datacenterId the datacenter ID (must be within 0~31)
     */
    private SnowflakeIdUtils(long workerId, long datacenterId) {
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * Sets custom worker and datacenter IDs at runtime.
     *
     * @param workerId     the worker ID
     * @param datacenterId the datacenter ID
     */
    public static void initialize(long workerId, long datacenterId) {
        synchronized (SnowflakeIdUtils.class) {
            instance = new SnowflakeIdUtils(workerId, datacenterId);
        }
    }

    /**
     * Ensures that the instance is initialized with default values if not already set.
     */
    public static void ensureInitialized() {
        if (instance == null) {
            synchronized (SnowflakeIdUtils.class) {
                if (instance == null) {
                    instance = new SnowflakeIdUtils(DEFAULT_WORKER_ID, DEFAULT_DATACENTER_ID);
                }
            }
        }
    }

    public static long nextId() {
        ensureInitialized();
        return instance.generateNextId();
    }

    public static String nextStringId() {
        ensureInitialized();
        return instance.generateNextStringId();
    }

    /**
     * Generates a 64-bit numeric ID.
     *
     * @return the generated ID
     */
    private long generateNextId() {
        return generateId(timeGen());
    }

    /**
     * Generates a string-based ID prefixed with timestamp.
     *
     * @return the generated string ID
     */
    private String generateNextStringId() {
        long timestamp = timeGen();
        long id = generateId(timestamp);

        long idTimestamp = (id >> TIMESTAMP_LEFT_SHIFT) + TWEPOCH;
        return FORMATTER.format(Instant.ofEpochMilli(idTimestamp)) + id;
    }

    /**
     * Core method to generate a unique ID based on timestamp and sequence.
     *
     * @param timestamp current timestamp
     * @return generated ID
     */
    private long generateId(long timestamp) {
        if (timestamp < lastTimestamp) {
            throw new IllegalArgumentException(
                    String.format("Clock moved backwards. Refusing to generate ID for %d milliseconds", lastTimestamp - timestamp));
        }

        if (lastTimestamp == timestamp) {
            long newSequence = sequence.updateAndGet(current -> (current + 1) & SEQUENCE_MASK);
            if (newSequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence.set(0L);
        }

        lastTimestamp = timestamp;

        return ((timestamp - TWEPOCH) << TIMESTAMP_LEFT_SHIFT) |
                (datacenterId << DATACENTER_ID_SHIFT) |
                (workerId << WORKER_ID_SHIFT) |
                sequence.get();
    }

    /**
     * Waits until next millisecond if the current timestamp is not greater than the last one.
     *
     * @param lastTimestamp previous timestamp
     * @return updated timestamp
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * Gets current timestamp in milliseconds.
     *
     * @return current timestamp
     */
    private long timeGen() {
        return System.currentTimeMillis();
    }

}
