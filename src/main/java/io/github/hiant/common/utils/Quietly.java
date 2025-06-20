package io.github.hiant.common.utils;

import java.io.Closeable;
import java.util.function.Consumer;

/**
 * Utility class to perform operations quietly, suppressing exceptions.
 *
 * <p>This class provides methods to safely close resources like {@link AutoCloseable} or
 * {@link Closeable} without throwing checked exceptions. It also allows executing
 * {@link Runnable} actions while optionally handling any thrown exceptions via a consumer.</p>
 *
 * @author liudong.work@gmail.com Created at: 2025/6/20 15:08
 */
public class Quietly {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private Quietly() {
    }

    /**
     * Closes an AutoCloseable silently, ignoring any exceptions.
     *
     * @param closeable the AutoCloseable to close; may be null
     */
    public static void close(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Closes a Closeable silently, ignoring any exceptions.
     *
     * @param closeable the Closeable to close; may be null
     */
    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Closes multiple Closeable resources silently.
     *
     * @param closeables the Closeable resources to close
     */
    public static void closeAll(Closeable... closeables) {
        for (Closeable c : closeables) {
            close(c);
        }
    }

    /**
     * Executes a Runnable action silently, catching and ignoring any exceptions.
     *
     * @param action the Runnable action to execute; may be null
     */
    public static void execute(Runnable action) {
        execute(action, null);
    }

    /**
     * Executes a Runnable action silently, catching any exceptions and passing them to the handler.
     *
     * @param action  the Runnable action to execute; may be null
     * @param handler optional exception handler; if not null, will be called with any caught exception
     */
    public static void execute(Runnable action, Consumer<Throwable> handler) {
        if (action != null) {
            try {
                action.run();
            } catch (Exception e) {
                if (handler != null) {
                    handler.accept(e);
                }
            }
        }
    }
}
