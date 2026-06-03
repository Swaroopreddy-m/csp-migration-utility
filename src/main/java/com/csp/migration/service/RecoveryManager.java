package com.csp.migration.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RecoveryManager {

    /**
     * Checks if any corresponding .js or .css files exist in the target output folders for the given HTML files.
     */
    public static boolean detectPreExistingFiles(List<Path> htmlFiles, Path jsFolder, Path cssFolder) {
        if (htmlFiles == null || htmlFiles.isEmpty()) {
            return false;
        }
        for (Path htmlFile : htmlFiles) {
            String baseName = getBaseName(htmlFile);
            Path jsPath = jsFolder.resolve(baseName + ".js");
            Path cssPath = cssFolder.resolve(baseName + ".css");
            if (Files.exists(jsPath) || Files.exists(cssPath)) {
                return true;
            }
        }
        return false;
    }

    public static String getBaseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }
}
