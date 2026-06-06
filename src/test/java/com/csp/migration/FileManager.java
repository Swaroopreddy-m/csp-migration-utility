package com.csp.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileManager {
    public static String readFile(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeFile(Path path, String content) throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        
        Path tempPath = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try {
            Files.write(tempPath, content.getBytes(StandardCharsets.UTF_8));
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Fallback if atomic move fails (e.g. cross-volume or OS limitations)
            try {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                // If move fails, try cleanup of temp file
                if (Files.exists(tempPath)) {
                    try { Files.delete(tempPath); } catch (Exception ignored) {}
                }
                throw ex;
            }
        }
    }
    
    public static void appendFile(Path path, String content) throws IOException {
        String existingContent = "";
        if (Files.exists(path)) {
            existingContent = readFile(path);
        }
        writeFile(path, existingContent + content);
    }
}
