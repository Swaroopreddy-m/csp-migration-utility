package com.csp.migration.service;

import com.csp.migration.exception.ExitRequestedException;
import com.csp.migration.exception.SkipFileException;
import com.csp.migration.model.ConversionReport;
import com.csp.migration.model.JsBlock;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ScriptExtractor {

    /**
     * Extracts local external scripts by reading their referenced files relative to the HTML file location.
     */
    public List<JsBlock> extractExternalScripts(Document doc, Path htmlFile, UserPrompter prompter, ConversionReport report)
            throws SkipFileException, ExitRequestedException {
        List<JsBlock> blocks = new ArrayList<>();
        Elements scripts = doc.select("script[src]");

        for (Element script : scripts) {
            String src = script.attr("src").trim();
            if (src.isEmpty()) {
                continue;
            }

            // Check if it is a remote script
            if (src.startsWith("http://") || src.startsWith("https://") || src.startsWith("//")) {
                report.incrementWarnings();
                LoggerService.warning("Remote script tag found and skipped for local merging in " + htmlFile.getFileName() + ": " + src);
                continue;
            }

            // Skip target JS file itself to prevent recursive imports
            String targetJsName = RecoveryManager.getBaseName(htmlFile) + ".js";
            if (src.equalsIgnoreCase(targetJsName)) {
                continue;
            }

            // Resolve local file path relative to HTML directory
            Path scriptPath = htmlFile.getParent().resolve(src).normalize();

            if (!Files.exists(scriptPath)) {
                LoggerService.warning("Script file not found: " + scriptPath.toAbsolutePath());
                UserPrompter.Choice choice = prompter.promptMissingScript(src, scriptPath.toAbsolutePath().toString());
                if (choice == UserPrompter.Choice.SKIP_FILE) {
                    report.incrementWarnings();
                    throw new SkipFileException("User skipped processing file due to missing script: " + src);
                } else if (choice == UserPrompter.Choice.EXIT) {
                    throw new ExitRequestedException("User chose to exit on missing script: " + src);
                } else { // Choice.CONTINUE
                    report.incrementWarnings();
                    LoggerService.info("User chose to continue despite missing script: " + src);
                    continue;
                }
            }

            try {
                String content = Files.readString(scriptPath);
                blocks.add(new JsBlock(JsBlock.Type.IMPORTED, src, 0, null, null, content));
            } catch (IOException e) {
                report.incrementErrors();
                LoggerService.error("Failed to read script file " + scriptPath.toAbsolutePath(), e);
            }
        }
        return blocks;
    }

    /**
     * Extracts internal scripts (content of script tags without src).
     */
    public List<JsBlock> extractInternalScripts(Document doc, ConversionReport report) {
        List<JsBlock> blocks = new ArrayList<>();
        Elements scripts = doc.select("script:not([src])");
        int index = 1;

        for (Element script : scripts) {
            String content = script.html();
            if (!content.trim().isEmpty()) {
                blocks.add(new JsBlock(JsBlock.Type.INTERNAL, null, index++, null, null, content));
            }
        }
        return blocks;
    }

    /**
     * Extracts inline event handlers (onclick, onchange, etc.) and converts them to external JS bindings.
     * Updates the DOM elements to add generated classes if necessary.
     */
    public List<JsBlock> extractInlineScripts(Document doc, ClassNameGenerator classGen, ConversionReport report) {
        List<JsBlock> blocks = new ArrayList<>();

        for (Element element : doc.getAllElements()) {
            List<Attribute> eventAttributes = new ArrayList<>();
            for (Attribute attr : element.attributes()) {
                String key = attr.getKey().toLowerCase();
                if (key.startsWith("on") && key.length() > 2) {
                    eventAttributes.add(attr);
                }
            }

            if (eventAttributes.isEmpty()) {
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

            for (Attribute attr : eventAttributes) {
                String key = attr.getKey().toLowerCase();
                String event = key.substring(2); // e.g. "click" from "onclick"
                String jsCode = attr.getValue();
                
                blocks.add(new JsBlock(JsBlock.Type.EVENT, null, 0, selector, event, jsCode));
            }
        }
        return blocks;
    }
}
