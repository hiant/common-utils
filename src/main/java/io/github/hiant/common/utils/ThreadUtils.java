package io.github.hiant.common.utils;

import lombok.NonNull;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Utility class providing thread-related utilities such as thread creation, sleep,
 * thread pool management, MDC context propagation, trace ID injection, etc.
 *
 * <p>This class provides convenience methods for working with threads and thread pools in Java.
 * It also supports wrapping runnables and callables to preserve MDC (Mapped Diagnostic Context)
 * across threads, which is particularly useful in logging and distributed tracing scenarios.</p>
 *
 * @author liudong.work@gmail.com Created at: 2025/6/12 13:37
 */
public class ThreadUtils {

    /**
     * The key used in MDC to store and retrieve the trace ID.
     */
    private static String traceIdKey = "tid";

    /**
     * Gets the current key used for trace ID in MDC.
     *
     * @return the current trace ID key
     */
    public static String getTraceIdKey() {
        return traceIdKey;
    }

    /**
     * Sets a new key to be used for storing trace ID in MDC.
     *
     * @param traceIdKey the new key to use for trace ID; must not be null
     */
    public static void setTraceIdKey(@NonNull String traceIdKey) {
        ThreadUtils.traceIdKey = traceIdKey;
    }

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private ThreadUtils() {
    }

    // === Sleep Methods ===

    /**
     * Sleeps for the given time using the specified time unit.
     *
     * @param unit     the time unit to use
     * @param duration the duration to sleep
     */
    public static void sleep(TimeUnit unit, long duration) {
        sleep(unit, duration, true);
    }

    /**
     * Sleeps for the given time using the specified time unit.
     *
     * @param unit        the time unit to use
     * @param duration    the duration to sleep
     * @param isInterrupt whether to re-interrupt the thread if interrupted
     */
    public static void sleep(TimeUnit unit, long duration, boolean isInterrupt) {
        sleep(unit, duration, isInterrupt, null);
    }

    /**
     * Sleeps for the given time using the specified time unit.
     *
     * @param unit        the time unit to use
     * @param duration    the duration to sleep
     * @param isInterrupt whether to re-interrupt the thread if interrupted
     * @param consumer    an optional exception handler for InterruptedException
     */
    public static void sleep(TimeUnit unit, long duration, boolean isInterrupt, Consumer<Throwable> consumer) {
        sleep(unit.toMillis(duration), isInterrupt, consumer);
    }

    /**
     * Sleeps for the given number of milliseconds.
     *
     * @param millis the number of milliseconds to sleep
     */
    public static void sleep(long millis) {
        sleep(millis, true, null);
    }

    /**
     * Sleeps for the given number of milliseconds.
     *
     * @param millis      the number of milliseconds to sleep
     * @param isInterrupt whether to re-interrupt the thread if interrupted
     */
    public static void sleep(long millis, boolean isInterrupt) {
        sleep(millis, isInterrupt, null);
    }

    /**
     * Sleeps for the given number of milliseconds.
     *
     * @param millis   the number of milliseconds to sleep
     * @param consumer an optional exception handler for InterruptedException
     */
    public static void sleep(long millis, Consumer<Throwable> consumer) {
        sleep(millis, true, consumer);
    }

    /**
     * Sleeps for the given number of milliseconds.
     *
     * @param millis      the number of milliseconds to sleep
     * @param isInterrupt whether to re-interrupt the thread if interrupted
     * @param consumer    an optional exception handler for InterruptedException
     */
    public static void sleep(long millis, boolean isInterrupt, Consumer<Throwable> consumer) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            if (isInterrupt) {
                Thread.currentThread().interrupt();
            }
            if (consumer != null) {
                consumer.accept(e);
            }
        }
    }

    // === Thread Creation Methods ===

    /**
     * Creates a new non-daemon thread with the given runnable.
     *
     * @param runnable the task to run in the new thread
     * @return a new thread instance
     */
    public static Thread newThread(Runnable runnable) {
        return newThread(runnable, false);
    }

    /**
     * Creates a new thread with the given runnable.
     *
     * @param runnable the task to run in the new thread
     * @param daemon   whether the thread should be a daemon thread
     * @return a new thread instance
     */
    public static Thread newThread(Runnable runnable, boolean daemon) {
        Thread thread = new Thread(wrap(runnable));
        thread.setDaemon(daemon);
        return thread;
    }

    /**
     * Creates a new non-daemon thread with the given name and runnable.
     *
     * @param runnable the task to run in the new thread
     * @param name     the name of the new thread
     * @return a new thread instance
     */
    public static Thread newThread(Runnable runnable, String name) {
        return newThread(runnable, name, false);
    }

    /**
     * Creates a new thread with the given name and runnable.
     *
     * @param runnable the task to run in the new thread
     * @param name     the name of the new thread
     * @param daemon   whether the thread should be a daemon thread
     * @return a new thread instance
     */
    public static Thread newThread(Runnable runnable, String name, boolean daemon) {
        Thread thread = new Thread(wrap(runnable), name);
        thread.setDaemon(daemon);
        return thread;
    }

    /**
     * Starts the given thread if it's not null.
     *
     * @param thread the thread to start
     */
    public static void start(Thread thread) {
        if (thread != null) {
            thread.start();
        }
    }

    /**
     * Checks whether the current thread has been interrupted.
     *
     * @return true if the current thread is interrupted, false otherwise
     */
    public static boolean isCurrentInterrupted() {
        return Thread.currentThread().isInterrupted();
    }

    // === Thread Pool Configuration Methods ===

    /**
     * Creates a new ThreadPoolTaskExecutor with custom configuration.
     *
     * @param threadNamePrefix prefix for thread names
     * @param corePoolSize     the core pool size
     * @param maxPoolSize      the maximum pool size
     * @param queueCapacity    the capacity of the task queue
     * @param keepAliveSeconds the keep-alive time in seconds
     * @param daemon           whether threads should be daemon threads
     * @param taskDecorator    optional task decorator
     * @return configured and initialized ThreadPoolTaskExecutor
     */
    public static ThreadPoolTaskExecutor newThreadPoolTaskExecutor(String threadNamePrefix, int corePoolSize, int maxPoolSize, int queueCapacity, int keepAliveSeconds, boolean daemon, TaskDecorator taskDecorator) {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(corePoolSize);
        threadPoolTaskExecutor.setMaxPoolSize(maxPoolSize);
        threadPoolTaskExecutor.setQueueCapacity(queueCapacity);
        threadPoolTaskExecutor.setThreadNamePrefix(threadNamePrefix);
        threadPoolTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        threadPoolTaskExecutor.setKeepAliveSeconds(keepAliveSeconds);
        threadPoolTaskExecutor.setDaemon(daemon);
        if (taskDecorator != null) {
            threadPoolTaskExecutor.setTaskDecorator(taskDecorator);
        }
        threadPoolTaskExecutor.initialize();
        return threadPoolTaskExecutor;
    }

    /**
     * Creates a custom ThreadFactory.
     *
     * @param poolName the prefix for thread names
     * @param priority the priority for created threads
     * @param daemon   whether threads should be daemon threads
     * @return a new ThreadFactory instance
     */
    public static ThreadFactory newThreadFactory(String poolName, int priority, boolean daemon) {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        AtomicInteger threadNumber = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(group, runnable, poolName + threadNumber.getAndIncrement(), 0);
            thread.setDaemon(daemon);
            thread.setPriority(priority);
            return thread;
        };
    }

    /**
     * Creates a new ThreadPoolExecutor with custom configuration.
     *
     * @param threadNamePrefix prefix for thread names
     * @param corePoolSize     the core pool size
     * @param maxPoolSize      the maximum pool size
     * @param queueCapacity    the capacity of the task queue
     * @param keepAliveSeconds the keep-alive time in seconds
     * @param priority         the priority of created threads
     * @param daemon           whether threads should be daemon threads
     * @return configured ThreadPoolExecutor
     */
    public static ThreadPoolExecutor newThreadPoolExecutor(String threadNamePrefix, int corePoolSize, int maxPoolSize, int queueCapacity, int keepAliveSeconds, int priority, boolean daemon) {
        BlockingQueue<Runnable> workQueue;
        if (queueCapacity <= 0) {
            workQueue = new SynchronousQueue<>();
        } else {
            workQueue = new LinkedBlockingQueue<>(queueCapacity);
        }
        return new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveSeconds, TimeUnit.SECONDS, workQueue, newThreadFactory(threadNamePrefix, priority, daemon), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Creates a ScheduledThreadPoolExecutor with custom configuration.
     *
     * @param threadNamePrefix prefix for thread names
     * @param corePoolSize     the core pool size
     * @param daemon           whether threads should be daemon threads
     * @param handler          the rejected execution handler
     * @return configured ScheduledThreadPoolExecutor
     */
    public static ScheduledThreadPoolExecutor newScheduledThreadPoolExecutor(String threadNamePrefix, int corePoolSize, boolean daemon, RejectedExecutionHandler handler) {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(threadNamePrefix);
            thread.setDaemon(daemon);
            return thread;
        };
        return new ScheduledThreadPoolExecutor(corePoolSize, factory, handler);
    }

    /**
     * Creates a new single-threaded executor.
     *
     * @param threadNamePrefix prefix for thread names
     * @param daemon           whether the thread should be a daemon thread
     * @return a new single-threaded executor
     */
    public static ExecutorService newSingleThreadExecutor(String threadNamePrefix, boolean daemon) {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(threadNamePrefix);
            thread.setDaemon(daemon);
            return thread;
        };
        return Executors.newSingleThreadExecutor(factory);
    }

    // === Runnable / Callable Wrapping Methods ===

    /**
     * Wraps a Runnable to preserve MDC context and inject trace ID.
     *
     * @param runnable the original runnable
     * @return a wrapped runnable that preserves MDC context
     */
    public static Runnable wrap(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(context);
            }
            setTraceIdIfAbsent();
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }

    /**
     * Wraps a Callable to preserve MDC context and inject trace ID.
     *
     * @param callable the original callable
     * @return a wrapped callable that preserves MDC context
     */
    public static <T> Callable<T> wrap(final Callable<T> callable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(context);
            }
            setTraceIdIfAbsent();
            try {
                return callable.call();
            } finally {
                MDC.clear();
            }
        };
    }

    /**
     * Sets a trace ID in MDC if none exists.
     */
    public static void setTraceIdIfAbsent() {
        if (MDC.get(ThreadUtils.traceIdKey) == null) {
            MDC.put(ThreadUtils.traceIdKey, SnowflakeIdUtils.nextStringId());
        }
    }

    // === Execution Methods ===

    /**
     * Executes the given command using the provided executor service with MDC preservation.
     *
     * @param executorService the executor service to use
     * @param command         the command to execute
     */
    public static void execute(ExecutorService executorService, Runnable command) {
        executorService.execute(wrap(command));
    }

    /**
     * Submits a Runnable task to the executor service for execution.
     *
     * @param executorService the executor service to use
     * @param task            the task to submit
     * @param result          the result to return upon successful completion
     * @return a Future representing pending completion of the task
     */
    public static <T> Future<T> submit(ExecutorService executorService, Runnable task, T result) {
        return executorService.submit(wrap(task), result);
    }

    /**
     * Submits a Callable task to the executor service for execution.
     *
     * @param executorService the executor service to use
     * @param task            the task to submit
     * @return a Future representing pending completion of the task
     */
    public static <T> Future<T> submit(ExecutorService executorService, Callable<T> task) {
        return executorService.submit(wrap(task));
    }

    /**
     * Submits a Runnable task to the executor service for execution.
     *
     * @param executorService the executor service to use
     * @param task            the task to submit
     * @return a Future representing pending completion of the task
     */
    public static Future<?> submit(ExecutorService executorService, Runnable task) {
        return executorService.submit(wrap(task));
    }
}
