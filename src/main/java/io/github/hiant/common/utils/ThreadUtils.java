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
 * @author liudong.work@gmail.com Created at: 2025/6/12 13:37
 */
public class ThreadUtils {

    private static String traceIdKey = "tid";

    public static String getTraceIdKey() {
        return traceIdKey;
    }

    public static void setTraceIdKey(@NonNull String traceIdKey) {
        ThreadUtils.traceIdKey = traceIdKey;
    }

    private ThreadUtils() {
    }

    public static void sleep(TimeUnit unit, long duration) {
        sleep(unit, duration, true);
    }

    public static void sleep(TimeUnit unit, long duration, boolean isInterrupt) {
        sleep(unit, duration, isInterrupt, null);
    }

    public static void sleep(TimeUnit unit, long duration, boolean isInterrupt, Consumer<Throwable> consumer) {
        sleep(unit.toMillis(duration), isInterrupt, consumer);
    }

    public static void sleep(long millis, boolean isInterrupt) {
        sleep(millis, isInterrupt, null);
    }

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

    public static ThreadPoolTaskExecutor newThreadPoolTaskExecutor(String threadNamePrefix, int corePoolSize, int maxPoolSize, int queueCapacity, int keepAliveSeconds, boolean daemon, TaskDecorator taskDecorator) {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(corePoolSize);
        threadPoolTaskExecutor.setMaxPoolSize(maxPoolSize);
        threadPoolTaskExecutor.setQueueCapacity(queueCapacity);
        threadPoolTaskExecutor.setThreadNamePrefix(threadNamePrefix);
        threadPoolTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        threadPoolTaskExecutor.setKeepAliveSeconds(keepAliveSeconds);
        threadPoolTaskExecutor.setDaemon(daemon);
        if (taskDecorator != null) {
            threadPoolTaskExecutor.setTaskDecorator(taskDecorator);
        }
        threadPoolTaskExecutor.initialize();
        return threadPoolTaskExecutor;
    }

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

    public static ThreadPoolExecutor newThreadPoolExecutor(String threadNamePrefix, int corePoolSize, int maxPoolSize, int queueCapacity, int keepAliveSeconds, int priority, boolean daemon) {
        BlockingQueue<Runnable> workQueue;
        if (queueCapacity <= 0) {
            workQueue = new SynchronousQueue<>();
        } else {
            workQueue = new LinkedBlockingQueue<>(queueCapacity);
        }
        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                workQueue,
                newThreadFactory(threadNamePrefix, priority, daemon),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public static ScheduledThreadPoolExecutor newScheduledThreadPoolExecutor(String threadNamePrefix, int corePoolSize, boolean daemon, RejectedExecutionHandler handler) {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(threadNamePrefix);
            thread.setDaemon(daemon);
            return thread;
        };
        return new ScheduledThreadPoolExecutor(corePoolSize, factory, handler);
    }

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

    public static void setTraceIdIfAbsent() {
        if (MDC.get(ThreadUtils.traceIdKey) == null) {
            MDC.put(ThreadUtils.traceIdKey, SnowflakeIdUtils.nextStringId());
        }
    }

}
