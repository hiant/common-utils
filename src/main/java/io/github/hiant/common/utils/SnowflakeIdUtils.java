package io.github.hiant.common.utils;

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

    private static volatile SnowflakeId instance = null;


    /**
     * Sets custom worker and datacenter IDs at runtime.
     *
     * @param workerId     the worker ID
     * @param datacenterId the datacenter ID
     */
    public synchronized static void initialize(long workerId, long datacenterId) {
        instance = new SnowflakeId(workerId, datacenterId);
    }

    /**
     * Ensures that the instance is initialized with default values if not already set.
     */
    public static void ensureInitialized() {
        if (instance == null) {
            synchronized (SnowflakeIdUtils.class) {
                if (instance == null) {
                    instance = new SnowflakeId();
                }
            }
        }
    }

    public static long nextId() {
        ensureInitialized();
        return instance.nextId();
    }

    public static String nextStringId() {
        ensureInitialized();
        return instance.nextStringId();
    }

}
