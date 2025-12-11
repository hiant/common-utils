package io.github.hiant.common.utils;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * Enhanced file watcher utility for monitoring file changes and triggering events
 * Features:
 * - Multi-file monitoring in the same directory
 * - Thread-safe Bean registration
 * - Automatic resource cleanup
 * - Configurable retry strategy
 */
@Slf4j
public class FileWatcher implements AutoCloseable {

    private final FileWatcherProperties properties;
    private final WatchService watchService;
    private final ScheduledExecutorService retryExecutor;
    private final Map<Path, Set<FileChangeListener>> directoryListeners = new ConcurrentHashMap<>();
    private final Map<Path, FileChangeListener> fileListeners = new ConcurrentHashMap<>();
    private final Map<Path, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    private final Set<Path> ignoredPaths = ConcurrentHashMap.newKeySet();
    private final Map<Path, FileAttributes> fileAttributesCache = new ConcurrentHashMap<>();
    private final ExecutorService watchExecutor;
    private volatile boolean running = true;

    /**
     * Constructs a new FileWatcher instance.
     *
     * @param properties The properties for configuring the FileWatcher.
     * @throws IOException If an I/O error occurs during WatchService creation.
     */
    public FileWatcher(FileWatcherProperties properties) throws IOException {
        this.properties = properties;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.watchExecutor = ThreadUtils.newSingleThreadExecutor("config-watcher-", true);
        this.retryExecutor = ThreadUtils.newScheduledThreadPoolExecutor("config-watcher-retry-", properties.getThreadPoolSize(), true, new ThreadPoolExecutor.CallerRunsPolicy());

        log.info("Config file watcher initialized with properties: {}", properties);

        // Start watch thread
        this.watchExecutor.submit(this::watchFiles);
        log.info("Config file watch thread started");
    }


    /**
     * Closes the FileWatcher, releasing all resources.
     * This includes shutting down the WatchService and all associated thread pools.
     */
    @Override
    public void close() {
        running = false;
        watchExecutor.shutdownNow();

        try {
            watchService.close();
            log.info("WatchService closed");
        } catch (IOException e) {
            log.error("Failed to close WatchService", e);
        }

        retryExecutor.shutdown();
        try {
            if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            retryExecutor.shutdownNow();
        }
        log.info("Config file watcher stopped");
    }


    /**
     * Registers a file listener for the specified file path.
     * The listener will be notified on file creation, modification, and deletion events.
     *
     * @param filePath Path to the file to monitor.
     * @param listener Listener to be notified on file changes.
     * @return The registered FileChangeListener instance.
     * @throws IOException              If file registration fails or an I/O error occurs.
     * @throws IllegalArgumentException If the provided file path is invalid or the listener path mismatches.
     */
    public FileChangeListener registerFileListener(@NonNull Path filePath, @NonNull FileChangeListener listener) throws IOException {
        filePath = normalizePath(filePath);

        if (!Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("Invalid file path: " + filePath + " is not a regular file");
        }

        Path dirPath = filePath.getParent();
        if (dirPath == null) {
            throw new IllegalArgumentException("Invalid file path: " + filePath);
        }

        // Validate listener path consistency
        if (!filePath.equals(listener.getFilePath())) {
            throw new IllegalArgumentException("Listener file path mismatch: " +
                    listener.getFilePath() + " vs " + filePath);
        }

        // Register directory watch if not already registered
        synchronized (directoryListeners) {
            if (!directoryListeners.containsKey(dirPath)) {
                dirPath.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                directoryListeners.put(dirPath, ConcurrentHashMap.newKeySet());
                log.info("Directory watch registered: {}", dirPath);
            }
            directoryListeners.get(dirPath).add(listener);
        }

        fileListeners.put(filePath, listener);

        // Check if file exists initially
        if (Files.exists(filePath)) {
            log.info("Existing file detected: {}", filePath);
            fileAttributesCache.put(filePath, getFileAttributes(filePath));
            scheduleFileProcessing(filePath, ENTRY_CREATE);
        } else {
            log.info("File being watched does not exist yet: {}", filePath);
        }
        return listener;
    }


    /**
     * Unregisters a file listener for the specified file path.
     * Stops monitoring changes for the given file.
     *
     * @param filePath Path to the file to unregister.
     */
    public void unregisterFileListener(@NonNull Path filePath) {
        filePath = normalizePath(filePath);

        Path dirPath = filePath.getParent();
        if (dirPath == null || !directoryListeners.containsKey(dirPath)) {
            return;
        }

        FileChangeListener listener = fileListeners.remove(filePath);
        if (listener != null) {
            synchronized (directoryListeners) {
                Set<FileChangeListener> listeners = directoryListeners.get(dirPath);
                listeners.remove(listener);

                // Remove directory watch if no more listeners
                if (listeners.isEmpty()) {
                    directoryListeners.remove(dirPath);
                    log.info("Directory watch removed: {}", dirPath);
                }
            }

            // Cleanup ignored paths and attributes cache
            ignoredPaths.remove(filePath);
            fileAttributesCache.remove(filePath);

            log.info("File listener unregistered: {}", filePath);
        }
    }


    /**
     * Ignores changes to a specific file, preventing further processing of its events.
     *
     * @param filePath Path to the file to ignore.
     */
    public void ignoreFile(@NonNull Path filePath) {
        ignoredPaths.add(normalizePath(filePath));
        log.info("File changes ignored: {}", filePath);
    }

    /**
     * Main loop for watching file system events.
     * This method runs in a dedicated thread and processes events from the WatchService.
     */
    private void watchFiles() {
        log.info("Started watching for file changes...");
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    // Handle overflow event
                    if (kind == OVERFLOW) {
                        log.warn("File system event overflow, some notifications may be lost");
                        continue;
                    }

                    // Get the directory where the event occurred
                    Path dir = (Path) key.watchable();

                    // Get the file path
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path contextPath = pathEvent.context();
                    Path fullPath = dir.resolve(contextPath).normalize();

                    // Add MDC context for logging
                    try (MDC.MDCCloseable mdc = createMdcContext(fullPath)) {
                        // Check if the file should be ignored
                        if (ignoredPaths.contains(fullPath)) {
                            log.debug("Ignoring file change event");
                            continue;
                        }

                        // Handle file creation event
                        if (kind == ENTRY_CREATE) {
                            if (Files.isRegularFile(fullPath)) {
                                log.info("File created: {}", fullPath);
                                fileAttributesCache.put(fullPath, getFileAttributes(fullPath));
                                scheduleFileProcessing(fullPath, ENTRY_CREATE);
                            }
                        }
                        // Handle file modification event with debounce
                        else if (kind == ENTRY_MODIFY) {
                            if (Files.isRegularFile(fullPath)) {
                                FileAttributes currentAttrs = getFileAttributes(fullPath);
                                FileAttributes cachedAttrs = fileAttributesCache.get(fullPath);

                                if (!currentAttrs.equals(cachedAttrs)) {
                                    log.info("File modified: {}", fullPath);
                                    fileAttributesCache.put(fullPath, currentAttrs);
                                    scheduleFileProcessing(fullPath, ENTRY_MODIFY);
                                } else {
                                    log.debug("Ignoring duplicate modification event");
                                }
                            }
                        }
                        // Handle file deletion event
                        else if (kind == ENTRY_DELETE) {
                            FileChangeListener listener;
                            synchronized (fileListeners) {
                                listener = fileListeners.get(fullPath);
                            }
                            cancelScheduledTask(fullPath);
                            if (listener != null) {
                                log.info("File deleted: {}", fullPath);
                                fileAttributesCache.remove(fullPath);
                                try {
                                    listener.onFileDelete(fullPath);
                                } catch (Exception e) {
                                    log.error("Failed to handle file deletion event", e);
                                }
                            }
                        }
                    }
                }

                // Reset the key
                boolean valid = key.reset();
                if (!valid) {
                    log.warn("WatchKey is no longer valid, stopping watching: {}", key.watchable());
                    break;
                }
            }
        } catch (ClosedWatchServiceException e) {
            if (running) {
                log.error("WatchService closed unexpectedly", e);
            } else {
                log.info("WatchService closed during shutdown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("File watch thread interrupted, shutting down");
        } catch (Exception e) {
            log.error("Unexpected error in file watch thread", e);
        } finally {
            log.info("File watch thread stopped");
        }
    }

    /**
     * Schedules file processing with a configurable delay and retry logic.
     * Debounces multiple events for the same file within a short period.
     *
     * @param filePath The path of the file to process.
     * @param type     The type of watch event (e.g., ENTRY_CREATE, ENTRY_MODIFY).
     */
    private void scheduleFileProcessing(Path filePath, WatchEvent.Kind<Path> type) {
        // Debounce: cancel any previously scheduled task for this file
        cancelScheduledTask(filePath);

        RetryTask task = new RetryTask(filePath, type);
        ScheduledFuture<?> newScheduledTask = retryExecutor.schedule(task, properties.getFileChangeDelay(), TimeUnit.MILLISECONDS);
        task.setSelfFuture(newScheduledTask);
        scheduledTasks.put(filePath, newScheduledTask);
    }

    private void cancelScheduledTask(Path filePath) {
        ScheduledFuture<?> existingTask = scheduledTasks.remove(filePath);
        if (existingTask != null) {
            existingTask.cancel(false); // Don't interrupt if already running
            log.debug("Cancelled pending task for file: {}", filePath);
        }
    }

    /**
     * Normalizes a given file path to its real, canonical form.
     * This helps in consistent path comparisons.
     *
     * @param path The path to normalize.
     * @return The normalized Path object.
     */
    private Path normalizePath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            log.warn("Failed to resolve real path for: {}. Using normalized path instead.", path, e);
            return path.normalize();
        }
    }

    /**
     * Retrieves basic file attributes for change detection, such as last modified time and size.
     *
     * @param filePath The path to the file.
     * @return A FileAttributes object containing the last modified time and size.
     */
    private FileAttributes getFileAttributes(Path filePath) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            return new FileAttributes(attrs.lastModifiedTime().toMillis(), attrs.size());
        } catch (IOException e) {
            log.error("Failed to read file attributes", e);
            return new FileAttributes(0, 0);
        }
    }

    /**
     * Creates an MDC (Mapped Diagnostic Context) closeable for logging, associating the file path with log entries.
     *
     * @param filePath The file path to add to the MDC.
     * @return An MDC.MDCCloseable instance that will remove the context when closed.
     */
    private MDC.MDCCloseable createMdcContext(Path filePath) {
        return MDC.putCloseable("filePath", filePath.toString());
    }

    /**
     * Represents basic file attributes (last modified time and size) for change detection.
     */
    @RequiredArgsConstructor
    private static class FileAttributes {
        private final long lastModified;
        private final long size;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FileAttributes that = (FileAttributes) o;
            return lastModified == that.lastModified && size == that.size;
        }

        @Override
        public int hashCode() {
            return Objects.hash(lastModified, size);
        }
    }

    /**
     * A runnable task that implements retry logic for file processing.
     * It attempts to process a file change event and retries with exponential backoff if it fails.
     */
    private class RetryTask implements Runnable {
        private final Path filePath;
        private final WatchEvent.Kind<Path> type;
        private int attempt = 0;
        private volatile ScheduledFuture<?> selfFuture;

        void setSelfFuture(ScheduledFuture<?> selfFuture) {
            this.selfFuture = selfFuture;
        }

        public RetryTask(Path filePath, WatchEvent.Kind<Path> type) {
            this.filePath = filePath;
            this.type = type;
        }

        @Override
        public void run() {
            boolean rescheduled = false;
            try (MDC.MDCCloseable ignored = createMdcContext(filePath)) {
                attempt++;
                log.info("Processing file change event (attempt {}): {}", attempt, type);

                if (attempt > 1) {
                    long delay = properties.isUseExponentialBackoff()
                            ? properties.getRetryBaseDelay() * (long) Math.pow(2, attempt - 2)
                            : properties.getRetryBaseDelay();
                    log.debug("Retry delay: {}ms", delay);
                }

                if (type == ENTRY_MODIFY && !Files.exists(filePath)) {
                    log.info("File no longer exists, skipping processing");
                    return;
                }

                FileChangeListener listener = fileListeners.get(filePath);
                if (listener != null) {
                    listener.onFileChange(filePath);
                    log.info("File processing completed successfully");
                }
            } catch (Exception e) {
                if (attempt < properties.getMaxRetryAttempts()) {
                    long nextDelay = properties.isUseExponentialBackoff()
                            ? properties.getRetryBaseDelay() * (long) Math.pow(2, attempt - 1)
                            : properties.getRetryBaseDelay();

                    log.warn("File processing failed (attempt {}), retrying in {}ms: {}",
                            attempt, nextDelay, e.getMessage());

                    ScheduledFuture<?> retry = retryExecutor.schedule(this, nextDelay, TimeUnit.MILLISECONDS);
                    setSelfFuture(retry);
                    scheduledTasks.put(filePath, retry);
                    rescheduled = true;
                } else {
                    log.error("File processing failed after maximum retries", e);
                }
            } finally {
                if (!rescheduled) {
                    scheduledTasks.remove(filePath, selfFuture);
                }
            }
        }
    }

}
