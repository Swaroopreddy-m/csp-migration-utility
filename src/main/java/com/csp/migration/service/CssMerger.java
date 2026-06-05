package com.csp.migration.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CssMerger {

    /**
     * Merges extracted inline styles into the CSS file at targetPath.
     * Overwrites matching properties for existing selectors; appends new selectors.
     */
    public static synchronized void mergeStylesIntoFile(Path targetPath, String selector, String inlineStyle, com.csp.migration.model.ConversionReport report) throws IOException {
        boolean existed = Files.exists(targetPath);
        String content = existed ? Files.readString(targetPath) : "";

        // Check if selector exists before merging
        String regex = "(?s)(?:^|\\s|\\}|,)" + Pattern.quote(selector) + "\\s*\\{";
        boolean selectorExisted = Pattern.compile(regex).matcher(content).find();

        String updatedContent = mergeStyleContent(content, selector, inlineStyle);
        FileService.writeStringTransactionally(targetPath, updatedContent);

        report.addInlineStyleExtracted(selector + " { " + inlineStyle + " } in " + targetPath.getFileName());

        if (selectorExisted) {
            report.incrementSelectorsMerged();
        } else {
            report.incrementSelectorsAdded();
        }

        if (existed) {
            report.addCssFileUpdated(targetPath.toAbsolutePath().toString());
        } else {
            report.addCssFileCreated(targetPath.toAbsolutePath().toString());
        }
    }

    /**
     * Helper to perform the string merge of style content.
     */
    public static String mergeStyleContent(String cssContent, String selector, String inlineStyle) {
        // Regex to match a CSS selector block:
        // Group 1: Start boundary, selector name, and opening brace
        // Group 2: Internal declarations
        // Group 3: Closing brace
        String regex = "(?s)((?:^|\\s|\\}|,)" + Pattern.quote(selector) + "\\s*\\{)([^}]*)(\\})";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(cssContent);

        Map<String, String> newProps = parseDeclarations(inlineStyle);

        if (matcher.find()) {
            // Selector block found, merge declarations
            String existingDeclStr = matcher.group(2);
            Map<String, String> mergedProps = parseDeclarations(existingDeclStr);
            mergedProps.putAll(newProps); // inline style overrides existing CSS

            String formattedProps = formatDeclarations(mergedProps);
            String replacement = matcher.group(1) + formattedProps + matcher.group(3);
            
            // Re-match to perform replacement at exact indexes
            return cssContent.substring(0, matcher.start()) + replacement + cssContent.substring(matcher.end());
        } else {
            // Selector not found, append to end
            StringBuilder sb = new StringBuilder(cssContent);
            if (cssContent.length() > 0 && !cssContent.endsWith("\n") && !cssContent.endsWith("\r")) {
                sb.append(System.lineSeparator());
            }
            sb.append(System.lineSeparator()).append(selector).append(" {");
            sb.append(formatDeclarations(newProps));
            sb.append("}").append(System.lineSeparator());
            return sb.toString();
        }
    }

    private static Map<String, String> parseDeclarations(String block) {
        Map<String, String> map = new LinkedHashMap<>();
        if (block == null) return map;
        String[] declarations = block.split(";");
        for (String dec : declarations) {
            dec = dec.trim();
            if (dec.isEmpty()) continue;
            int colon = dec.indexOf(':');
            if (colon != -1) {
                String key = dec.substring(0, colon).trim().toLowerCase();
                String val = dec.substring(colon + 1).trim();
                map.put(key, val);
            }
        }
        return map;
    }

    private static String formatDeclarations(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            sb.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append(";").append(System.lineSeparator());
        }
        return sb.toString();
    }
}
