package com.csp.migration.service;

import com.csp.migration.exception.ExitRequestedException;
import com.csp.migration.exception.SkipFileException;
import com.csp.migration.model.ConversionReport;
import com.csp.migration.model.CssBlock;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StyleExtractor {

    /**
     * Extracts local external styles by reading their referenced files relative to the HTML file location.
     */
    public List<CssBlock> extractExternalStyles(Document doc, Path htmlFile, UserPrompter prompter, ConversionReport report)
            throws SkipFileException, ExitRequestedException {
        List<CssBlock> blocks = new ArrayList<>();
        Elements styles = doc.select("link[rel=stylesheet]");

        for (Element style : styles) {
            String href = style.attr("href").trim();
            if (href.isEmpty()) {
                continue;
            }

            // Check if it is a remote style
            if (href.startsWith("http://") || href.startsWith("https://") || href.startsWith("//")) {
                report.incrementWarnings();
                LoggerService.warning("Remote stylesheet found and skipped for local merging in " + htmlFile.getFileName() + ": " + href);
                continue;
            }

            // Skip target CSS file itself to prevent recursive imports
            String targetCssName = RecoveryManager.getBaseName(htmlFile) + ".css";
            if (href.equalsIgnoreCase(targetCssName)) {
                continue;
            }

            // Resolve local file path relative to HTML directory
            Path stylePath = htmlFile.getParent().resolve(href).normalize();

            if (!Files.exists(stylePath)) {
                LoggerService.warning("Style file not found: " + stylePath.toAbsolutePath());
                UserPrompter.Choice choice = prompter.promptMissingStyle(href, stylePath.toAbsolutePath().toString());
                if (choice == UserPrompter.Choice.SKIP_FILE) {
                    report.incrementWarnings();
                    throw new SkipFileException("User skipped processing file due to missing stylesheet: " + href);
                } else if (choice == UserPrompter.Choice.EXIT) {
                    throw new ExitRequestedException("User chose to exit on missing stylesheet: " + href);
                } else { // Choice.CONTINUE
                    report.incrementWarnings();
                    LoggerService.info("User chose to continue despite missing stylesheet: " + href);
                    continue;
                }
            }

            try {
                String content = Files.readString(stylePath);
                blocks.add(new CssBlock(CssBlock.Type.IMPORTED, href, 0, null, content));
            } catch (IOException e) {
                report.incrementErrors();
                LoggerService.error("Failed to read stylesheet file " + stylePath.toAbsolutePath(), e);
            }
        }
        return blocks;
    }

    /**
     * Extracts internal styles (content of style tags).
     */
    public List<CssBlock> extractInternalStyles(Document doc, ConversionReport report) {
        List<CssBlock> blocks = new ArrayList<>();
        Elements styles = doc.select("style");
        int index = 1;

        for (Element style : styles) {
            String content = style.html();
            if (!content.trim().isEmpty()) {
                blocks.add(new CssBlock(CssBlock.Type.INTERNAL, null, index++, null, content));
            }
        }
        return blocks;
    }

    /**
     * Extracts inline styles (style attribute) and converts them to external CSS rules.
     * Updates the DOM elements to add generated classes or IDs if necessary.
     */
    public List<CssBlock> extractInlineStyles(Document doc, Path htmlFile, ConversionReport report) {
        List<CssBlock> blocks = new ArrayList<>();
        Elements elementsWithStyle = doc.select("[style]");
        String fileName = htmlFile.getFileName().toString();

        java.util.Set<String> existingIds = new java.util.HashSet<>();
        for (Element el : doc.getAllElements()) {
            if (el.hasAttr("id") && !el.attr("id").trim().isEmpty()) {
                existingIds.add(el.attr("id").trim());
            }
        }
        
        java.util.concurrent.atomic.AtomicInteger idCounter = new java.util.concurrent.atomic.AtomicInteger(scanExistingIds(doc));

        for (Element element : elementsWithStyle) {
            String styleContent = element.attr("style").trim();
            if (styleContent.isEmpty()) {
                continue;
            }

            // 1. Parse and migrate display properties
            String[] declarations = styleContent.split(";");
            StringBuilder remainingStyleBuilder = new StringBuilder();
            boolean hasDisplay = false;
            String displayVal = "";

            for (String dec : declarations) {
                dec = dec.trim();
                if (dec.isEmpty()) continue;
                int colon = dec.indexOf(':');
                if (colon != -1) {
                    String prop = dec.substring(0, colon).trim().toLowerCase();
                    String val = dec.substring(colon + 1).trim().replace("'", "").replace("\"", "").toLowerCase();
                    if ("display".equals(prop)) {
                        hasDisplay = true;
                        displayVal = val;
                        continue;
                    }
                }
                if (remainingStyleBuilder.length() > 0) {
                    remainingStyleBuilder.append(" ");
                }
                remainingStyleBuilder.append(dec).append(";");
            }

            if (hasDisplay) {
                if ("none".equals(displayVal)) {
                    element.attr("hidden", "");
                    report.addDisplayNoneConversion(String.format("Migrated display:none to hidden attribute on <%s> in %s", element.tagName(), fileName));
                } else if ("block".equals(displayVal) || displayVal.isEmpty()) {
                    element.removeAttr("hidden");
                    report.addDisplayNoneConversion(String.format("Ensured display:%s element <%s> is not hidden in %s", displayVal, element.tagName(), fileName));
                }
            }

            String remainingStyle = remainingStyleBuilder.toString().trim();
            if (remainingStyle.isEmpty()) {
                element.removeAttr("style");
                continue;
            }

            // Update style attribute with remaining styles
            element.attr("style", remainingStyle);

            // 2. Determine selector according to priority
            String selector;
            String elementId = element.attr("id").trim();
            String elementName = element.attr("name").trim();

            if (!elementId.isEmpty()) {
                // Priority 1 & 2: Reuse existing ID
                selector = "#" + elementId;
                report.addExistingIdReused(elementId);
            } else if (!elementName.isEmpty()) {
                // Priority 3: No ID but Name exists
                // Ensure name is unique to be used as ID
                if (!existingIds.contains(elementName)) {
                    element.attr("id", elementName);
                    existingIds.add(elementName);
                    selector = "#" + elementName;
                    report.addIdGenerated(elementName);
                } else {
                    // Fallback to auto ID if name is not unique as ID
                    String generatedId = generateUniqueId(existingIds, idCounter);
                    element.attr("id", generatedId);
                    selector = "#" + generatedId;
                    report.addIdGenerated(generatedId);
                    report.addManualReviewWarning(String.format("Name '%s' in <%s> is not unique as an ID on page %s. Generated '%s' instead.", elementName, element.tagName(), fileName, generatedId));
                }
            } else {
                // Priority 4: Generate unique ID
                String generatedId = generateUniqueId(existingIds, idCounter);
                element.attr("id", generatedId);
                selector = "#" + generatedId;
                report.addIdGenerated(generatedId);
            }

            blocks.add(new CssBlock(CssBlock.Type.INLINE, null, 0, selector, remainingStyle));
            
            // Clean up style attribute since it is now extracted
            element.removeAttr("style");
        }
        return blocks;
    }

    private int scanExistingIds(Document doc) {
        int max = 0;
        Pattern pattern = Pattern.compile("csp_auto_(\\d{4})");
        for (Element el : doc.getAllElements()) {
            if (el.hasAttr("id")) {
                String id = el.attr("id").trim();
                Matcher m = pattern.matcher(id);
                if (m.matches()) {
                    try {
                        int val = Integer.parseInt(m.group(1));
                        if (val > max) max = val;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return max;
    }

    private String generateUniqueId(java.util.Set<String> existingIds, java.util.concurrent.atomic.AtomicInteger counter) {
        while (true) {
            String id = String.format("csp_auto_%04d", counter.incrementAndGet());
            if (!existingIds.contains(id)) {
                existingIds.add(id);
                return id;
            }
        }
    }
}
