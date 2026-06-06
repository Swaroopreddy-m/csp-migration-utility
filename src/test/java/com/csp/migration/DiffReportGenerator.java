package com.csp.migration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DiffReportGenerator {
    public static void writeDiffReport(Path htmlFile, String originalContent, String modifiedContent) {
        String baseName = htmlFile.getFileName().toString();
        int dot = baseName.lastIndexOf('.');
        if (dot != -1) {
            baseName = baseName.substring(0, dot);
        }
        
        Path diffFile = htmlFile.getParent().resolve(baseName + "_diff.txt");
        String diffContent = generateDiff(originalContent, modifiedContent);
        
        try {
            FileManager.writeFile(diffFile, diffContent);
            LoggerManager.info("Generated diff report: " + diffFile.getFileName());
        } catch (IOException e) {
            LoggerManager.error("Failed to write diff report for " + htmlFile, e);
        }
    }

    public static String generateDiff(String original, String modified) {
        String[] origLines = original.split("\\r?\\n");
        String[] modLines = modified.split("\\r?\\n");

        List<String> diffLog = new ArrayList<>();
        int max = Math.max(origLines.length, modLines.length);

        for (int idx = 0; idx < max; idx++) {
            if (idx < origLines.length && idx < modLines.length) {
                if (!origLines[idx].equals(modLines[idx])) {
                    diffLog.add("Line " + (idx + 1) + " modified");
                }
            } else if (idx < modLines.length) {
                diffLog.add("Line " + (idx + 1) + " added");
            } else {
                diffLog.add("Line " + (idx + 1) + " removed");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Original\n");
        sb.append("=========================================\n");
        sb.append(original).append("\n\n");
        sb.append("Modified\n");
        sb.append("=========================================\n");
        sb.append(modified).append("\n\n");
        sb.append("Differences\n");
        sb.append("=========================================\n");
        if (diffLog.isEmpty()) {
            sb.append("No changes detected.\n");
        } else {
            for (String diff : diffLog) {
                sb.append(diff).append("\n");
            }
        }

        return sb.toString();
    }
}
