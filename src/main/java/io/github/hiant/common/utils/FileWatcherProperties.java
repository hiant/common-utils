package io.github.hiant.common.utils;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Configuration properties for the file watcher component.
 * This class holds settings that control the behavior of the file monitoring system.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class FileWatcherProperties {

    /**
     * The size of the thread pool used to handle file change events.
     * Determines how many events can be processed concurrently.
     * Default: 5 threads
     */
    int threadPoolSize = 5;

    /**
     * Delay time (in milliseconds) before processing a detected file change.
     * Helps avoid rapid, repeated triggers from the file system.
     * Default: 200 ms
     */
    int fileChangeDelay = 200;

    /**
     * Maximum number of retry attempts when handling a failed file change event.
     * Useful for transient failures such as file locking or temporary I/O issues.
     * Default: 3 attempts
     */
    int maxRetryAttempts = 3;

    /**
     * Base delay time (in milliseconds) between retries.
     * Actual delay may be adjusted based on retry strategy.
     * Default: 100 ms
     */
    long retryBaseDelay = 100;

    /**
     * Whether to use exponential backoff strategy for retries.
     * If true, the retry delay increases exponentially with each attempt.
     * Default: true
     */
    boolean useExponentialBackoff = true;
}
