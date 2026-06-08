package com.csp.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HtmlScanner {
    public static List<Path> scanForHtmlFiles(Path startPath) {
        List<Path> htmlFiles = new ArrayList<>();
        if (Files.isRegularFile(startPath)) {
            String lower = startPath.toString().toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".jsp")) {
                htmlFiles.add(startPath);
            }
            return htmlFiles;
        }
        try (Stream<Path> stream = Files.walk(startPath)) {
            htmlFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !isIgnoredDirectory(p))
                    .filter(p -> {
                        String lower = p.toString().toLowerCase();
                        return lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".jsp");
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LoggerManager.error("Failed to scan directory for HTML/JSP files: " + startPath, e);
        }
        return htmlFiles;
    }

    private static boolean isIgnoredDirectory(Path p) {
        for (Path segment : p) {
            String name = segment.toString();
            if (name.equals("node_modules") || 
                name.equals(".git") || 
                name.equals("dist") || 
                name.equals("target") || 
                name.equals("build")) {
                return true;
            }
        }
        return false;
    }
}
