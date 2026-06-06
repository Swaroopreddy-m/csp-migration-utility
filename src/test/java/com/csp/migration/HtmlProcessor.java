package com.csp.migration;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import com.csp.migration.exception.ExitRequestedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class HtmlProcessor {
    private final Path htmlFile;
    private final Path htmlRoot;
    private final Path jsFolder;
    private final Path cssFolder;
    private final String contextPath;

    public HtmlProcessor(Path htmlFile, Path htmlRoot, Path jsFolder, Path cssFolder, String contextPath) {
        this.htmlFile = htmlFile;
        this.htmlRoot = htmlRoot;
        this.jsFolder = jsFolder;
        this.cssFolder = cssFolder;
        this.contextPath = contextPath;
    }

    public void process() throws ExitRequestedException, Exception {
        LoggerManager.info("Processing file: " + htmlFile.toAbsolutePath());
        
        String originalContent = FileManager.readFile(htmlFile);
        ScriptExtractor.checkMalformedHtmlComments(originalContent, htmlFile.getFileName().toString());

        Document doc = Jsoup.parse(originalContent);
        String baseName = htmlFile.getFileName().toString();
        int dot = baseName.lastIndexOf('.');
        if (dot != -1) {
            baseName = baseName.substring(0, dot);
        }

        Path targetJsFile = jsFolder.resolve(baseName + ".js");
        Path targetCssFile = cssFolder.resolve(baseName + ".css");

        String externalJs = "";
        String internalJs = "";
        String inlineJs = "";

        String externalCss = "";
        String internalCss = "";
        String inlineCss = "";

        // 1. External scripts stage
        List<String> extScripts = ScriptExtractor.extractExternalScripts(doc, htmlFile, htmlRoot, contextPath);
        StringBuilder sbExtJs = new StringBuilder();
        for (String js : extScripts) {
            sbExtJs.append(js).append("\n\n");
        }
        externalJs = sbExtJs.toString();
        
        InputManager.confirmStage(
            "External scripts stage", 
            "External scripts extracted successfully. Remove original script imports from HTML?"
        );
        doc.select("script[src]").remove();

        // 2. Internal scripts stage
        List<String> intScripts = ScriptExtractor.extractInternalScripts(doc, htmlFile.getFileName().toString());
        StringBuilder sbIntJs = new StringBuilder();
        for (String js : intScripts) {
            sbIntJs.append(js).append("\n\n");
        }
        internalJs = sbIntJs.toString();

        InputManager.confirmStage(
            "Internal scripts stage",
            "Internal scripts extracted successfully. Remove original script blocks?"
        );
        doc.select("script:not([src])").remove();

        // 3. Inline scripts stage
        IdGenerator idGen = new IdGenerator(baseName);
        inlineJs = InlineScriptConverter.convertInlineScripts(doc, idGen, false);

        InputManager.confirmStage(
            "Inline scripts stage",
            "Inline scripts converted successfully. Remove original inline handlers?"
        );
        removeInlineScriptHandlers(doc);

        // 4. External CSS stage
        List<String> extCss = StyleExtractor.extractExternalStylesheets(doc, htmlFile, htmlRoot, contextPath);
        StringBuilder sbExtCss = new StringBuilder();
        for (String css : extCss) {
            sbExtCss.append(css).append("\n\n");
        }
        externalCss = sbExtCss.toString();

        InputManager.confirmStage(
            "External CSS stage",
            "External CSS extracted successfully. Remove original link elements?"
        );
        doc.select("link[rel=stylesheet]").remove();

        // 5. Internal CSS stage
        List<String> intCss = StyleExtractor.extractInternalStyles(doc, htmlFile.getFileName().toString());
        StringBuilder sbIntCss = new StringBuilder();
        for (String css : intCss) {
            sbIntCss.append(css).append("\n\n");
        }
        internalCss = sbIntCss.toString();

        InputManager.confirmStage(
            "Internal CSS stage",
            "Internal CSS extracted successfully. Remove original style blocks?"
        );
        doc.select("style").remove();

        // 6. Inline CSS stage
        inlineCss = InlineStyleConverter.convertInlineStyles(doc, idGen, false);

        InputManager.confirmStage(
            "Inline CSS stage",
            "Inline CSS converted successfully. Remove original style attributes?"
        );
        removeInlineStyles(doc);

        // Resolve duplicates & merge using DuplicateResolver
        String finalJsCode = DuplicateResolver.resolveScriptDuplicates(externalJs, internalJs, inlineJs);
        String finalCssCode = DuplicateResolver.resolveCssDuplicates(externalCss, internalCss, inlineCss);

        // Write output files (or append if existing/loaded)
        if (Files.exists(targetJsFile)) {
            String existingJs = FileManager.readFile(targetJsFile);
            finalJsCode = DuplicateResolver.resolveScriptDuplicates(existingJs, "", finalJsCode);
        }
        if (Files.exists(targetCssFile)) {
            String existingCss = FileManager.readFile(targetCssFile);
            finalCssCode = DuplicateResolver.resolveCssDuplicates(existingCss, "", finalCssCode);
        }

        FileManager.writeFile(targetJsFile, finalJsCode);
        FileManager.writeFile(targetCssFile, finalCssCode);

        String relJsPath = getRelativePath(htmlFile, targetJsFile);
        String relCssPath = getRelativePath(htmlFile, targetCssFile);

        Element head = doc.head();
        if (head == null) {
            head = doc.appendElement("head");
        }
        
        head.appendElement("link")
            .attr("rel", "stylesheet")
            .attr("href", relCssPath);
            
        head.appendElement("script")
            .attr("src", relJsPath);

        Elements metaCsp = doc.select("meta[http-equiv=Content-Security-Policy]");
        if (metaCsp.isEmpty()) {
            head.appendElement("meta")
                .attr("http-equiv", "Content-Security-Policy")
                .attr("content", "default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none';");
        }

        String modifiedContent = doc.outerHtml();
        FileManager.writeFile(htmlFile, modifiedContent);

        ValidationManager.validate(modifiedContent, htmlFile, htmlRoot, contextPath, finalJsCode, finalCssCode, relJsPath, relCssPath);
        DiffReportGenerator.writeDiffReport(htmlFile, originalContent, modifiedContent);
    }

    private static void removeInlineScriptHandlers(Document doc) {
        for (Element element : doc.getAllElements()) {
            List<String> toRemove = new ArrayList<>();
            for (org.jsoup.nodes.Attribute attr : element.attributes()) {
                String key = attr.getKey().toLowerCase();
                if (key.startsWith("on") && key.length() > 2) {
                    toRemove.add(attr.getKey());
                }
            }
            for (String key : toRemove) {
                element.removeAttr(key);
            }
        }
    }

    private static void removeInlineStyles(Document doc) {
        for (Element element : doc.select("[style]")) {
            element.removeAttr("style");
        }
    }

    private static String getRelativePath(Path from, Path to) {
        try {
            Path relative = from.getParent().toAbsolutePath().normalize().relativize(to.toAbsolutePath().normalize());
            return relative.toString().replace('\\', '/');
        } catch (Exception e) {
            return to.getFileName().toString();
        }
    }
}
