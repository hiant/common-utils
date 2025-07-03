package io.github.hiant.common.utils;

import java.nio.file.Path;

/**
 * Interface for listeners that want to be notified of file system changes.
 */
public interface FileChangeListener {

    /**
     * Returns the path of the file this listener is associated with.
     *
     * @return The file path.
     */
    Path getFilePath();

    /**
     * Called when the content of the monitored file changes.
     *
     * @param filePath The path to the changed file.
     */
    void onFileChange(Path filePath);

    /**
     * Called when the monitored file is deleted.
     * Default implementation does nothing.
     *
     * @param filePath The path to the deleted file.
     */
    default void onFileDelete(Path filePath) {
        // Default implementation is empty
    }
}
