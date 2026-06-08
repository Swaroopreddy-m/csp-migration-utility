package com.csp.migration;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ScriptExtractor {
    public static List<String> extractExternalScripts(Document doc, Path htmlFile, Path htmlRoot, String contextPath) {
        List<String> contents = new ArrayList<>();
        Elements scriptTags = doc.select("script[src]");

        for (Element tag : scriptTags) {
            String src = tag.attr("src").trim();
            if (src.isEmpty()) {
                continue;
            }

            if (src.startsWith("http://") || src.startsWith("https://") || src.startsWith("//")) {
                LoggerManager.info("Skipping remote script: " + src);
                continue;
            }

            Path resolvedPath = ResourceResolver.resolveResource(src, htmlFile, htmlRoot, contextPath, true, true);
            if (resolvedPath != null && Files.exists(resolvedPath)) {
                try {
                    String content = FileManager.readFile(resolvedPath);
                    checkMalformedJsComments(content, resolvedPath.getFileName().toString());
                    contents.add(content);
                } catch (IOException e) {
                    LoggerManager.error("Failed to read script file: " + resolvedPath, e);
                }
            } else {
                LoggerManager.warn("External script file not found: " + src);
            }
        }
        return contents;
    }

    public static List<String> extractInternalScripts(Document doc, String htmlFileName) {
        List<String> contents = new ArrayList<>();
        Elements scriptTags = doc.select("script:not([src])");
        for (Element tag : scriptTags) {
            String content = tag.html();
            if (!content.trim().isEmpty()) {
                checkMalformedJsComments(content, "internal script block in " + htmlFileName);
                contents.add(content);
            }
        }
        return contents;
    }

    public static void checkMalformedJsComments(String jsContent, String fileName) {
        int openIndex = 0;
        int closeIndex = 0;
        int openCount = 0;
        int closeCount = 0;
        while ((openIndex = jsContent.indexOf("/*", openIndex)) != -1) {
            openCount++;
            openIndex += 2;
        }
        while ((closeIndex = jsContent.indexOf("*/", closeIndex)) != -1) {
            closeCount++;
            closeIndex += 2;
        }
        if (openCount != closeCount) {
            LoggerManager.warn("Malformed JavaScript comment detected in " + fileName);
        }
    }

    public static void checkMalformedHtmlComments(String html, String fileName) {
        int openIndex = 0;
        int closeIndex = 0;
        int openCount = 0;
        int closeCount = 0;
        while ((openIndex = html.indexOf("<!--", openIndex)) != -1) {
            openCount++;
            openIndex += 4;
        }
        while ((closeIndex = html.indexOf("-->", closeIndex)) != -1) {
            closeCount++;
            closeIndex += 3;
        }
        if (openCount != closeCount) {
            LoggerManager.warn("Malformed HTML comment detected in " + fileName);
        }
    }
}
