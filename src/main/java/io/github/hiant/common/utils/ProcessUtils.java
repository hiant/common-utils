package io.github.hiant.common.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * ProcessUtils provides a cross-platform, thread-safe utility to execute system commands.
 *
 * <p>Optimization notes:
 * <ul>
 *   <li>Thread pool uses CallerRunsPolicy to avoid task rejection under saturation.</li>
 *   <li>Constants for thread pool size, queue capacity, output line limits, and read timeout division.</li>
 *   <li>Interrupt status is preserved when catching InterruptedException.</li>
 *   <li>toString summary method is compatible with JDK 1.8 (no use of String.lines()).</li>
 *   <li>Compatible with JDK 1.8 by removing ProcessHandle API usage and replacing String.lines().</li>
 * </ul>
 *
 * <p>Features:
 * <ul>
 *   <li>Cross-platform support: automatically selects Windows cmd.exe or Unix shell.</li>
 *   <li>Daemon thread pool with bounded size and queue, threads named for clarity.</li>
 *   <li>JVM shutdown hook gracefully shuts down the thread pool.</li>
 *   <li>Output truncation at 10,000 lines to prevent OutOfMemoryError.</li>
 *   <li>Stream read timeout set to 1/5 of total process timeout to avoid hangs.</li>
 *   <li>Process destruction (JDK 1.8 limited to main process, no recursive child process kill).</li>
 *   <li>Supports real-time consumption of stdout/stderr via callbacks.</li>
 *   <li>Returns ProcessResult with indication if output was truncated.</li>
 * </ul>
 */
public class ProcessUtils {
    private static final int MAX_OUTPUT_LINES = 10_000;
    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 20;
    private static final int QUEUE_CAPACITY = 100;
    private static final int READ_TIMEOUT_DIVIDER = 5;

    private static final ThreadPoolExecutor EXECUTOR = ThreadUtils.newThreadPoolExecutor("ProcessUtils-Worker-", CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY, 60, Thread.NORM_PRIORITY, true);

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            EXECUTOR.shutdown();
            try {
                if (!EXECUTOR.awaitTermination(3, TimeUnit.SECONDS)) {
                    EXECUTOR.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "ProcessUtils-ShutdownHook"));
    }

    /**
     * Executes a single command string via the system shell, with a default timeout of 10 seconds.
     * When no callbacks are provided, stdout and stderr are collected and truncated if too long.
     * <p>Note: uses the platform shell (`cmd.exe` or `/bin/sh -c`); prefer {@link #runDirect(List)} for untrusted input.</p>
     *
     * @param command The command string to execute.
     * @return ProcessResult containing execution details.
     * @throws IOException          if an I/O error occurs.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     * @throws ExecutionException   if reading output fails.
     * @throws TimeoutException     if the command times out.
     */
    public static ProcessResult run(String command)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        return run(command, 10);
    }

    /**
     * Executes a single command string via the system shell, with a default timeout of 10 seconds.
     * When no callbacks are provided, stdout and stderr are collected and truncated if too long.
     *
     * @param command The command string to execute.
     * @param timeout Maximum time to wait for process completion.
     * @return ProcessResult containing execution details.
     * @throws IOException          if an I/O error occurs.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     * @throws ExecutionException   if reading output fails.
     * @throws TimeoutException     if the command times out.
     */
    public static ProcessResult run(String command, long timeout)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        return run(
                Arrays.asList(resolveShell(), resolveShellFlag(), command),
                Duration.ofSeconds(timeout),
                Charset.defaultCharset(),
                null,
                null
        );
    }

    /**
     * Executes a command without spawning a shell (arguments are passed directly).
     * Default timeout: 10 seconds.
     *
     * @param command command and arguments (each element is one argument)
     * @return ProcessResult containing execution details.
     * @throws IOException          if an I/O error occurs.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     * @throws ExecutionException   if reading output fails.
     * @throws TimeoutException     if the command times out.
     */
    public static ProcessResult runDirect(List<String> command)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        return run(command, Duration.ofSeconds(10), Charset.defaultCharset(), null, null);
    }

    /**
     * Executes a command without spawning a shell (arguments are passed directly).
     *
     * @param command command and arguments (each element is one argument)
     * @param timeout Maximum time to wait for process completion in seconds.
     * @return ProcessResult containing execution details.
     * @throws IOException          if an I/O error occurs.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     * @throws ExecutionException   if reading output fails.
     * @throws TimeoutException     if the command times out.
     */
    public static ProcessResult runDirect(List<String> command, long timeout)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        return run(command, Duration.ofSeconds(timeout), Charset.defaultCharset(), null, null);
    }


    /**
     * Executes a command with full control over parameters and callbacks.
     *
     * @param command        Command and arguments as a list of strings.
     * @param timeout        Maximum time to wait for process completion.
     * @param charset        Charset used to decode output streams.
     * @param stdoutConsumer Line-based callback for standard output, or null to collect output.
     * @param stderrConsumer Line-based callback for error output, or null to collect output.
     * @return ProcessResult containing exit code, output, errors, and truncation flags.
     * @throws IOException          if an I/O error occurs.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     * @throws ExecutionException   if reading output fails.
     * @throws TimeoutException     if the process times out.
     */
    public static ProcessResult run(
            List<String> command,
            Duration timeout,
            Charset charset,
            Consumer<String> stdoutConsumer,
            Consumer<String> stderrConsumer
    ) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(command, "command cannot be null");

        Process process = new ProcessBuilder(command).start();

        // Close stdin to prevent blocking on input.
        process.getOutputStream().close();

        Future<StreamResult> stdoutFuture = readStream(process.getInputStream(), charset, stdoutConsumer);
        Future<StreamResult> stderrFuture = readStream(process.getErrorStream(), charset, stderrConsumer);

        // Wait for process exit within timeout.
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            destroyProcessTree(process);
            throw new TimeoutException("Process timeout after " + timeout);
        }

        Duration readTimeout = timeout.dividedBy(READ_TIMEOUT_DIVIDER);
        StreamResult stdoutResult = getWithTimeout(stdoutFuture, readTimeout);
        StreamResult stderrResult = getWithTimeout(stderrFuture, readTimeout);

        return new ProcessResult(
                process.exitValue(),
                stdoutResult.content,
                stderrResult.content,
                stdoutResult.truncated,
                stderrResult.truncated
        );
    }

    private static Future<StreamResult> readStream(
            InputStream in, Charset charset, Consumer<String> consumer) {
        return EXECUTOR.submit(() -> {
            boolean truncated = false;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, charset))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    if (consumer != null) {
                        consumer.accept(line);
                    } else {
                        sb.append(line).append(System.lineSeparator());
                        if (++count >= MAX_OUTPUT_LINES) {
                            truncated = true;
                            sb.append("...output truncated...").append(System.lineSeparator());
                            break;
                        }
                    }
                }
            }
            return new StreamResult(sb.toString().trim(), truncated);
        });
    }

    private static StreamResult getWithTimeout(
            Future<StreamResult> future, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw ie;
        } catch (TimeoutException te) {
            future.cancel(true);
            throw new TimeoutException("Stream read timeout after " + timeout);
        }
    }

    /**
     * Attempts to forcibly destroy the process.
     * <p>
     * Note: JDK 1.8 does not support destroying child processes recursively.
     *
     * @param process the Process to destroy
     */
    private static void destroyProcessTree(Process process) {
        if (process == null) return;
        try {
            process.destroyForcibly();
            Quietly.close(process.getInputStream());
            Quietly.close(process.getErrorStream());
            Quietly.close(process.getOutputStream());
        } catch (Exception ignored) {
            // Log if needed
        }
    }

    private static String resolveShell() {
        return isWindows()
                ? "cmd.exe"
                : System.getenv().getOrDefault("SHELL", "/bin/sh");
    }

    private static String resolveShellFlag() {
        return isWindows() ? "/c" : "-c";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Result wrapper for process execution.
     */
    public static class ProcessResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final boolean stdoutTruncated;
        public final boolean stderrTruncated;

        public ProcessResult(
                int exitCode,
                String stdout,
                String stderr,
                boolean stdoutTruncated,
                boolean stderrTruncated
        ) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.stdoutTruncated = stdoutTruncated;
            this.stderrTruncated = stderrTruncated;
        }

        /**
         * Returns true if the process exited with code 0.
         */
        public boolean isSuccess() {
            return exitCode == 0;
        }

        @Override
        public String toString() {
            String outSnippet = summarize(stdout);
            String errSnippet = summarize(stderr);
            return String.format(
                    "ProcessResult{exit=%d, stdoutTruncated=%s, stderrTruncated=%s, stdout=\"%s\", stderr=\"%s\"}",
                    exitCode, stdoutTruncated, stderrTruncated, outSnippet, errSnippet
            );
        }

        /**
         * Summarizes the text by extracting up to the first 5 lines.
         * Compatible with JDK 1.8 (no use of String.lines()).
         */
        private String summarize(String text) {
            if (text == null || text.isEmpty()) {
                return "";
            }
            BufferedReader br = new BufferedReader(new StringReader(text));
            StringBuilder sb = new StringBuilder();
            try {
                String line;
                int count = 0;
                while ((line = br.readLine()) != null && count < 5) {
                    if (count > 0) {
                        sb.append("\\n");
                    }
                    sb.append(line);
                    count++;
                }
            } catch (IOException ignored) {
                // StringReader does not throw IOException, safe to ignore
            }
            return sb.toString();
        }
    }

    /**
     * Internal holder for stream read results
     */
    private static class StreamResult {
        final String content;
        final boolean truncated;

        StreamResult(String content, boolean truncated) {
            this.content = content;
            this.truncated = truncated;
        }
    }

    /**
     * Gracefully shutdown the thread pool
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
    }

    /**
     * Immediately shutdown the thread pool
     */
    public static void shutdownNow() {
        EXECUTOR.shutdownNow();
    }
}
