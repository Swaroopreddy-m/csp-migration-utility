package com.csp.migration.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class FileService {

    /**
     * Writes content to a file in a transaction-safe/atomic manner.
     * It writes to a temporary file in the same directory first, and then atomically renames/moves it.
     */
    public static void writeStringTransactionally(Path targetPath, String content) throws IOException {
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        } else {
            parent = Paths.get(".");
        }

        // Create temp file in the same directory to ensure atomic move on the same filesystem
        String fileName = targetPath.getFileName().toString();
        Path tempPath = Files.createTempFile(parent, fileName, ".tmp");

        try {
            Files.writeString(tempPath, content, StandardCharsets.UTF_8);
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tempPath);
            throw e;
        }
    }
}
