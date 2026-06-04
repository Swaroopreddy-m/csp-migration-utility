package com.csp.migration.service;

import com.csp.migration.model.ConversionReport;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ReportWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void writeReports(Path outputDir, ConversionReport report) {
        try {
            // Write JSON report
            Path jsonPath = outputDir.resolve("migration_report.json");
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("htmlFilesProcessed", report.getHtmlFilesList());
            jsonMap.put("cssFilesResolved", report.getCssFilesList());
            jsonMap.put("jsFilesResolved", report.getJsFilesList());
            jsonMap.put("missingResources", report.getMissingResources());
            jsonMap.put("userResourceMappings", report.getUserResourceMappings());
            jsonMap.put("inlineStylesExtracted", report.getInlineStylesExtracted());
            jsonMap.put("existingIdsReused", report.getExistingIdsReused());
            jsonMap.put("idsGenerated", report.getIdsGenerated());
            jsonMap.put("displayNoneConversions", report.getDisplayNoneConversions());
            jsonMap.put("jsVisibilityConversions", report.getJsVisibilityConversions());
            jsonMap.put("warnings", report.getManualReviewWarnings());
            
            String jsonContent = GSON.toJson(jsonMap);
            FileService.writeStringTransactionally(jsonPath, jsonContent);
            LoggerService.info("Written JSON migration report to: " + jsonPath.toAbsolutePath());

            // Write Markdown report
            Path mdPath = outputDir.resolve("migration_report.md");
            String mdContent = generateMarkdownReport(report);
            FileService.writeStringTransactionally(mdPath, mdContent);
            LoggerService.info("Written Markdown migration report to: " + mdPath.toAbsolutePath());

        } catch (IOException e) {
            LoggerService.error("Failed to generate migration reports", e);
        }
    }

    private static String generateMarkdownReport(ConversionReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# CSP Migration Utility - Execution Report").append(System.lineSeparator()).append(System.lineSeparator());

        sb.append("## Summary Metrics").append(System.lineSeparator()).append(System.lineSeparator());
        sb.append("- **HTML Files Processed**: ").append(report.getHtmlFilesList().size()).append(System.lineSeparator());
        sb.append("- **CSS Files Resolved**: ").append(report.getCssFilesList().size()).append(System.lineSeparator());
        sb.append("- **JS Files Resolved**: ").append(report.getJsFilesList().size()).append(System.lineSeparator());
        sb.append("- **Total Warnings/Manual Reviews**: ").append(report.getWarnings()).append(System.lineSeparator());
        sb.append("- **Total Errors**: ").append(report.getErrors()).append(System.lineSeparator()).append(System.lineSeparator());

        // Processed HTML Files
        sb.append("## Processed HTML Files").append(System.lineSeparator());
        if (report.getHtmlFilesList().isEmpty()) {
            sb.append("*None*").append(System.lineSeparator());
        } else {
            for (String file : report.getHtmlFilesList()) {
                sb.append("- ").append(file).append(System.lineSeparator());
            }
        }
        sb.append(System.lineSeparator());

        // Resolved CSS Files
        sb.append("## Resolved CSS Files").append(System.lineSeparator());
        if (report.getCssFilesList().isEmpty()) {
            sb.append("*None*").append(System.lineSeparator());
        } else {
            for (String file : report.getCssFilesList()) {
                sb.append("- ").append(file).append(System.lineSeparator());
            }
        }
        sb.append(System.lineSeparator());

        // Resolved JS Files
        sb.append("## Resolved JS Files").append(System.lineSeparator());
        if (report.getJsFilesList().isEmpty()) {
            sb.append("*None*").append(System.lineSeparator());
        } else {
            for (String file : report.getJsFilesList()) {
                sb.append("- ").append(file).append(System.lineSeparator());
            }
        }
        sb.append(System.lineSeparator());

        // Missing resources and mappings
        sb.append("## Missing Resources & Mappings").append(System.lineSeparator());
        sb.append("### Missing Resources").append(System.lineSeparator());
        if (report.getMissingResources().isEmpty()) {
            sb.append("*None*").append(System.lineSeparator());
        } else {
            for (String res : report.getMissingResources()) {
                sb.append("- ").append(res).append(System.lineSeparator());
            }
        }
        sb.append(System.lineSeparator());

        sb.append("### User Supplied Path Mappings").append(System.lineSeparator());
        if (report.getUserResourceMappings().isEmpty()) {
            sb.append("*None*").append(System.lineSeparator());
        } else {
            sb.append("| Original Path | Resolved Path |").append(System.lineSeparator());
            sb.append("|---|---|").append(System.lineSeparator());
            for (Map.Entry<String, String> entry : report.getUserResourceMappings().entrySet()) {
                sb.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" |").append(System.lineSeparator());
            }
        }
        sb.append(System.lineSeparator());

        // Inline styling details
        sb.append("## Inline Style Extraction").append(System.lineSeparator());
        sb.append("- **Existing IDs Reused**: ").append(report.getExistingIdsReused().size()).append(System.lineSeparator());
        sb.append("- **New IDs Generated**: ").append(report.getIdsGenerated().size()).append(System.lineSeparator()).append(System.lineSeparator());

        sb.append("### Extracted Styles").append(System.lineSeparator());
        if (report.getInlineStylesExtracted().isEmpty()) {
            sb.append("*None*").append(System.lineSeparator());
        } else {
            for (String style : report.getInlineStylesExtracted()) {
                sb.append("- ").append(style).append(System.lineSeparator());
            }
        }
        sb.append(System.lineSeparator());

        // Display Conversions
        sb.append("## Inline Display Conversions").append(System.lineSeparator());
        if (report.getDisplayNoneConversions().isEmpty()) {
            sb.append("*None*").append(System.lineSeparator());
        } else {
            for (String conv : report.getDisplayNoneConversions()) {
                sb.append("- ").append(conv).append(System.lineSeparator());
            }
        }
        sb.append(System.lineSeparator());

        // JS visibility conversions
        sb.append("## JS Visibility Conversions").append(System.lineSeparator());
        if (report.getJsVisibilityConversions().isEmpty()) {
            sb.append("*None*").append(System.lineSeparator());
        } else {
            for (String conv : report.getJsVisibilityConversions()) {
                sb.append("- ").append(conv).append(System.lineSeparator());
            }
        }
        sb.append(System.lineSeparator());

        // Warnings for manual review
        sb.append("## Warnings Requiring Manual Review").append(System.lineSeparator());
        if (report.getManualReviewWarnings().isEmpty()) {
            sb.append("*None - All checks passed cleanly!*").append(System.lineSeparator());
        } else {
            for (String warn : report.getManualReviewWarnings()) {
                sb.append("- ").append(warn).append(System.lineSeparator());
            }
        }
        sb.append(System.lineSeparator());

        return sb.toString();
    }
}
