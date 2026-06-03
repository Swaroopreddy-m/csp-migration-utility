package com.csp.migration.service;

import com.csp.migration.model.ConversionReport;
import com.csp.migration.model.CssBlock;
import com.csp.migration.model.JsBlock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class UpdateManager {

    public static class UpdateResult {
        public int added;
        public int skipped;

        public UpdateResult(int added, int skipped) {
            this.added = added;
            this.skipped = skipped;
        }
    }

    /**
     * Updates an existing JS file or creates it if missing.
     * Preserves custom code, skips duplicate blocks, and maintains class mappings.
     */
    public static UpdateResult updateJsFile(Path targetPath, List<JsBlock> newBlocks, ConversionReport report) throws IOException {
        int added = 0;
        int skipped = 0;
        List<JsBlock> mergedBlocks = new ArrayList<>();
        String customCode = "";

        if (Files.exists(targetPath)) {
            String content = Files.readString(targetPath);
            JsGenerator.DeserializedJs deserialized = JsGenerator.parseJsContent(content);
            mergedBlocks.addAll(deserialized.getBlocks());
            customCode = deserialized.getCustomCode();
        }

        for (JsBlock block : newBlocks) {
            if (mergedBlocks.contains(block)) {
                skipped++;
            } else {
                mergedBlocks.add(block);
                added++;
            }
        }

        // Write the updated file atomically
        String finalJs = JsGenerator.generateJsContent(mergedBlocks, customCode);
        FileService.writeStringTransactionally(targetPath, finalJs);

        // Update the report
        report.addNewScriptsAdded(added);
        report.addDuplicateScriptsRemoved(skipped); // Skipped/Removed are equivalent for duplicates
        if (added > 0 || skipped > 0) {
            LoggerService.update(String.format("JS file updated at %s: New Scripts Added : %d, Duplicate Scripts Skipped : %d",
                    targetPath.getFileName(), added, skipped));
        }

        return new UpdateResult(added, skipped);
    }

    /**
     * Updates an existing CSS file or creates it if missing.
     * Preserves custom style rules, skips duplicates, and maintains class mappings.
     */
    public static UpdateResult updateCssFile(Path targetPath, List<CssBlock> newBlocks, ConversionReport report) throws IOException {
        int added = 0;
        int skipped = 0;
        List<CssBlock> mergedBlocks = new ArrayList<>();
        String customCss = "";

        if (Files.exists(targetPath)) {
            String content = Files.readString(targetPath);
            CssGenerator.DeserializedCss deserialized = CssGenerator.parseCssContent(content);
            mergedBlocks.addAll(deserialized.getBlocks());
            customCss = deserialized.getCustomCss();
        }

        for (CssBlock block : newBlocks) {
            if (mergedBlocks.contains(block)) {
                skipped++;
            } else {
                mergedBlocks.add(block);
                added++;
            }
        }

        // Write the updated CSS file atomically
        String finalCss = CssGenerator.generateCssContent(mergedBlocks, customCss);
        FileService.writeStringTransactionally(targetPath, finalCss);

        // Update the report
        report.addNewStylesAdded(added);
        report.addDuplicateStylesRemoved(skipped);
        if (added > 0 || skipped > 0) {
            LoggerService.update(String.format("CSS file updated at %s: New Styles Added : %d, Duplicate Styles Skipped : %d",
                    targetPath.getFileName(), added, skipped));
        }

        return new UpdateResult(added, skipped);
    }
}
