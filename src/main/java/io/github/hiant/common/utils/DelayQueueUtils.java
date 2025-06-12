package io.github.hiant.common.utils;

import java.lang.reflect.Field;
import java.util.PriorityQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;

/**
 * @author liudong.work@gmail.com Created at: 2025/6/12 18:15
 */
public class DelayQueueUtils {

    private DelayQueueUtils() {
    }

    /**
     * Creates a new DelayQueue with the specified initial capacity.
     * <p>
     * This method constructs an empty DelayQueue and then uses reflection to set its internal PriorityQueue
     * to the given initial capacity. This is useful when the expected size of the queue is known in advance,
     * to avoid unnecessary resizing overhead.
     *
     * @param initialCapacity the initial capacity of the internal PriorityQueue
     * @param <T>             the type of elements held in the queue, must be a subtype of {@link Delayed}
     * @return a new DelayQueue instance configured with the specified initial capacity
     * @throws RuntimeException if modifying the internal state of DelayQueue fails due to reflection issues
     */
    public static <T extends Delayed> DelayQueue<T> newDelayQueue(int initialCapacity) {
        return extendInitialCapacity(new DelayQueue<>(), initialCapacity);
    }


    /**
     * Modifies the internal PriorityQueue of the given DelayQueue to a new one with specified capacity.
     * <p>
     * WARNING: This method uses reflection to modify internal state of DelayQueue, which is an invasive operation.
     * Use it only when necessary and ensure that the queue is not in active use during this operation.
     *
     * @param queue           The DelayQueue instance to modify (must not be null)
     * @param initialCapacity The new initial capacity for the PriorityQueue
     * @return The modified DelayQueue with updated internal PriorityQueue
     * @throws RuntimeException if reflection operations fail
     */
    public static <T extends Delayed> DelayQueue<T> extendInitialCapacity(DelayQueue<T> queue, int initialCapacity) {
        if (queue == null) {
            throw new IllegalArgumentException("DelayQueue must not be null");
        }
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("Initial capacity must be greater than 0");
        }

        try {
            Field field = DelayQueue.class.getDeclaredField("q");
            field.setAccessible(true);
            field.set(queue, new PriorityQueue<>(initialCapacity));
            return queue;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Field 'q' not found in DelayQueue", e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to access or modify field 'q'", e);
        }
    }
}
