package com.csp.migration.service;

import com.csp.migration.model.ConversionReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsRewriter {

    private static final Pattern DISPLAY_PATTERN = Pattern.compile("(\\b[\\w\\$.]+)\\.style\\.display\\s*=\\s*(['\"])(none|block|)\\2\\s*;?");
    private static final Pattern LITERAL_JQUERY_PATTERN = Pattern.compile("\\$\\(\\s*(['\"])(.*?)\\1\\s*\\)\\.(hide|show)\\(\\s*\\)\\s*;?");
    private static final Pattern VARIABLE_JQUERY_PATTERN = Pattern.compile("\\$\\(\\s*([\\w\\$.]+|this)\\s*\\)\\.(hide|show)\\(\\s*\\)\\s*;?");

    // Scanning patterns for visibility-related warnings
    private static final Pattern WARNING_VISIBILITY = Pattern.compile("\\.style\\.visibility\\s*=");
    private static final Pattern WARNING_JQ_CSS = Pattern.compile("\\$\\(\\s*.*?\\s*\\)\\.css\\(\\s*['\"](display|visibility)['\"]");
    private static final Pattern WARNING_JQ_METHODS = Pattern.compile("\\$\\(\\s*.*?\\s*\\)\\.(toggle|fadeIn|fadeOut)\\(");

    /**
     * Scans and rewrites the JavaScript file at targetPath, recording updates and manual review warnings.
     */
    public static synchronized void rewriteJsFile(Path targetPath, ConversionReport report) throws IOException {
        if (!Files.exists(targetPath)) {
            return;
        }

        List<String> lines = Files.readAllLines(targetPath);
        List<String> updatedLines = new ArrayList<>();
        boolean modified = false;
        String fileName = targetPath.getFileName().toString();

        for (int i = 0; i < lines.size(); i++) {
            String originalLine = lines.get(i);
            String line = originalLine;
            int lineNumber = i + 1;

            // 1. Process style.display
            Matcher displayMatcher = DISPLAY_PATTERN.matcher(line);
            while (displayMatcher.find()) {
                String originalStatement = displayMatcher.group(0);
                String element = displayMatcher.group(1);
                String value = displayMatcher.group(3);
                boolean isHidden = "none".equals(value);
                String replacement = element + ".hidden = " + isHidden + ";";
                
                line = line.replace(originalStatement, replacement);
                modified = true;
                
                report.addJsVisibilityConversion(String.format("Line %d: %s -> %s in %s", lineNumber, originalStatement, replacement, fileName));
                report.incrementJsStyleDisplayConversions(1);
                LoggerService.update(String.format("Converted display in %s:%d: %s -> %s", fileName, lineNumber, originalStatement, replacement));
                // Recalculate matcher because string was modified
                displayMatcher = DISPLAY_PATTERN.matcher(line);
            }

            // 2. Process jQuery hide/show (literal string selectors)
            Matcher jqLiteralMatcher = LITERAL_JQUERY_PATTERN.matcher(line);
            while (jqLiteralMatcher.find()) {
                String originalStatement = jqLiteralMatcher.group(0);
                String quote = jqLiteralMatcher.group(1);
                String selector = jqLiteralMatcher.group(2);
                String action = jqLiteralMatcher.group(3);
                boolean isHidden = "hide".equals(action);
                String replacement = "document.querySelector(" + quote + selector + quote + ").hidden = " + isHidden + ";";
                
                line = line.replace(originalStatement, replacement);
                modified = true;

                report.addJsVisibilityConversion(String.format("Line %d: %s -> %s in %s", lineNumber, originalStatement, replacement, fileName));
                if (isHidden) {
                    report.incrementJsHideConversions(1);
                } else {
                    report.incrementJsShowConversions(1);
                }
                LoggerService.update(String.format("Converted jQuery visibility in %s:%d: %s -> %s", fileName, lineNumber, originalStatement, replacement));
                jqLiteralMatcher = LITERAL_JQUERY_PATTERN.matcher(line);
            }

            // 3. Process jQuery hide/show (variable selectors)
            Matcher jqVarMatcher = VARIABLE_JQUERY_PATTERN.matcher(line);
            while (jqVarMatcher.find()) {
                String originalStatement = jqVarMatcher.group(0);
                String element = jqVarMatcher.group(1);
                String action = jqVarMatcher.group(2);
                boolean isHidden = "hide".equals(action);
                String replacement = element + ".hidden = " + isHidden + ";";
                
                line = line.replace(originalStatement, replacement);
                modified = true;

                report.addJsVisibilityConversion(String.format("Line %d: %s -> %s in %s", lineNumber, originalStatement, replacement, fileName));
                if (isHidden) {
                    report.incrementJsHideConversions(1);
                } else {
                    report.incrementJsShowConversions(1);
                }
                LoggerService.update(String.format("Converted jQuery visibility in %s:%d: %s -> %s", fileName, lineNumber, originalStatement, replacement));
                jqVarMatcher = VARIABLE_JQUERY_PATTERN.matcher(line);
            }

            // 3. Scan for warnings requiring manual review
            scanForWarnings(originalLine, lineNumber, fileName, report);

            updatedLines.add(line);
        }

        if (modified) {
            String content = String.join(System.lineSeparator(), updatedLines) + System.lineSeparator();
            FileService.writeStringTransactionally(targetPath, content);
        }
    }

    private static void scanForWarnings(String line, int lineNumber, String fileName, ConversionReport report) {
        if (WARNING_VISIBILITY.matcher(line).find()) {
            report.addManualReviewWarning(String.format("JS file %s line %d: Uses visibility styling (%s)", fileName, lineNumber, line.trim()));
        }
        if (WARNING_JQ_CSS.matcher(line).find()) {
            report.addManualReviewWarning(String.format("JS file %s line %d: Uses jQuery .css(display/visibility) (%s)", fileName, lineNumber, line.trim()));
        }
        if (WARNING_JQ_METHODS.matcher(line).find()) {
            report.addManualReviewWarning(String.format("JS file %s line %d: Uses jQuery transition/toggle (%s)", fileName, lineNumber, line.trim()));
        }
    }
}
