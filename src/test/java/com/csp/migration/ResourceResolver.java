package com.csp.migration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ResourceResolver {
    private static final Map<String, String> RESOLVED_MAPPINGS = new HashMap<>();
    private static boolean ignoreAllMissing = false;

    public static synchronized void reset() {
        RESOLVED_MAPPINGS.clear();
        ignoreAllMissing = false;
    }

    public static Path resolveResource(String srcPath, Path currentPageFile, Path htmlRoot, String contextPath) {
        if (srcPath == null || srcPath.trim().isEmpty()) {
            return null;
        }
        
        String src = srcPath.trim();
        
        synchronized (RESOLVED_MAPPINGS) {
            if (RESOLVED_MAPPINGS.containsKey(src)) {
                String mapped = RESOLVED_MAPPINGS.get(src);
                return mapped != null ? Paths.get(mapped) : null;
            }
        }

        String substituted = src;
        if (src.contains("DYN_CONTEXT_PATH")) {
            String ctx = contextPath != null ? contextPath : "";
            substituted = src.replace("${DYN_CONTEXT_PATH}", ctx).replace("DYN_CONTEXT_PATH", ctx);
        }

        Path resolved = tryResolvePriorities(substituted, currentPageFile, htmlRoot);
        
        if (resolved == null && !substituted.equals(src)) {
            resolved = tryResolvePriorities(src, currentPageFile, htmlRoot);
        }

        if (resolved != null && Files.exists(resolved) && Files.isRegularFile(resolved)) {
            synchronized (RESOLVED_MAPPINGS) {
                RESOLVED_MAPPINGS.put(src, resolved.toAbsolutePath().toString());
            }
            return resolved;
        }

        synchronized (ResourceResolver.class) {
            if (ignoreAllMissing) {
                LoggerManager.warn("Resource not found (Ignored-All): " + src);
                return null;
            }
        }

        int choice = InputManager.promptMissingResource(src);
        if (choice == 1) {
            while (true) {
                String entered = InputManager.readLine("Enter correct path: ");
                if (entered.isEmpty()) {
                    continue;
                }
                Path p = Paths.get(entered).toAbsolutePath().normalize();
                if (Files.exists(p) && Files.isRegularFile(p)) {
                    synchronized (RESOLVED_MAPPINGS) {
                        RESOLVED_MAPPINGS.put(src, p.toString());
                    }
                    return p;
                }
                System.out.println("File does not exist. Try again.");
            }
        } else if (choice == 3) {
            synchronized (ResourceResolver.class) {
                ignoreAllMissing = true;
            }
            LoggerManager.warn("Resource not found (Ignored-All initiated): " + src);
            return null;
        } else {
            LoggerManager.warn("Resource not found (Ignored): " + src);
            return null;
        }
    }

    private static Path tryResolvePriorities(String pathStr, Path currentPageFile, Path htmlRoot) {
        Path pageDir = currentPageFile.getParent();
        if (pageDir == null) {
            pageDir = Paths.get(".").toAbsolutePath().normalize();
        }
        Path candidate = pageDir.resolve(pathStr).normalize();
        if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
            return candidate;
        }

        try {
            Path absCandidate = Paths.get(pathStr).toAbsolutePath().normalize();
            if (Files.exists(absCandidate) && Files.isRegularFile(absCandidate)) {
                return absCandidate;
            }
        } catch (Exception ignored) {}

        Path filenameOnly = Paths.get(pathStr).getFileName();
        if (filenameOnly != null) {
            Path pageDirFile = pageDir.resolve(filenameOnly.toString()).normalize();
            if (Files.exists(pageDirFile) && Files.isRegularFile(pageDirFile)) {
                return pageDirFile;
            }
        }

        Path rootCandidate = htmlRoot.resolve(pathStr).normalize();
        if (Files.exists(rootCandidate) && Files.isRegularFile(rootCandidate)) {
            return rootCandidate;
        }
        if (filenameOnly != null) {
            Path rootFilenameCandidate = htmlRoot.resolve(filenameOnly.toString()).normalize();
            if (Files.exists(rootFilenameCandidate) && Files.isRegularFile(rootFilenameCandidate)) {
                return rootFilenameCandidate;
            }
        }

        return null;
    }
}
