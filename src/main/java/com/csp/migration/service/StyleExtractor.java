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
     * Updates the DOM elements to add generated classes if necessary.
     */
    public List<CssBlock> extractInlineStyles(Document doc, ClassNameGenerator classGen, ConversionReport report) {
        List<CssBlock> blocks = new ArrayList<>();
        Elements elementsWithStyle = doc.select("[style]");

        for (Element element : elementsWithStyle) {
            String styleContent = element.attr("style").trim();
            if (styleContent.isEmpty()) {
                continue;
            }

            // Determine selector for this element
            String selector;
            if (element.hasAttr("class") && !element.attr("class").trim().isEmpty()) {
                String classAttr = element.attr("class").trim();
                selector = "." + String.join(".", classAttr.split("\\s+"));
            } else if (element.hasAttr("id") && !element.attr("id").trim().isEmpty()) {
                selector = "#" + element.attr("id").trim();
            } else {
                String generatedClass = classGen.generateNext();
                element.addClass(generatedClass);
                report.incrementGeneratedClasses();
                selector = "." + generatedClass;
            }

            blocks.add(new CssBlock(CssBlock.Type.INLINE, null, 0, selector, styleContent));
        }
        return blocks;
    }
}
