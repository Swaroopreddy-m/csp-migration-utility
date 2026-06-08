package com.csp.migration;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StyleExtractor {
    public static List<String> extractExternalStylesheets(Document doc, Path htmlFile, Path htmlRoot, String contextPath) {
        List<String> contents = new ArrayList<>();
        Elements linkTags = doc.select("link[rel=stylesheet]");

        for (Element tag : linkTags) {
            String href = tag.attr("href").trim();
            if (href.isEmpty()) {
                continue;
            }

            if (href.startsWith("http://") || href.startsWith("https://") || href.startsWith("//")) {
                LoggerManager.info("Skipping remote stylesheet: " + href);
                continue;
            }

            Path resolvedPath = ResourceResolver.resolveResource(href, htmlFile, htmlRoot, contextPath);
            if (resolvedPath != null && Files.exists(resolvedPath)) {
                try {
                    String content = FileManager.readFile(resolvedPath);
                    checkMalformedCssComments(content, resolvedPath.getFileName().toString());
                    contents.add(content);
                } catch (IOException e) {
                    LoggerManager.error("Failed to read stylesheet file: " + resolvedPath, e);
                }
            } else {
                LoggerManager.warn("External stylesheet file not found: " + href);
            }
        }
        return contents;
    }

    public static List<String> extractInternalStyles(Document doc, String htmlFileName) {
        List<String> contents = new ArrayList<>();
        Elements styleTags = doc.select("style");
        for (Element tag : styleTags) {
            String content = tag.html();
            if (!content.trim().isEmpty()) {
                checkMalformedCssComments(content, "internal style block in " + htmlFileName);
                contents.add(content);
            }
        }
        return contents;
    }

    public static void checkMalformedCssComments(String cssContent, String fileName) {
        int openIndex = 0;
        int closeIndex = 0;
        int openCount = 0;
        int closeCount = 0;
        while ((openIndex = cssContent.indexOf("/*", openIndex)) != -1) {
            openCount++;
            openIndex += 2;
        }
        while ((closeIndex = cssContent.indexOf("*/", closeIndex)) != -1) {
            closeCount++;
            closeIndex += 2;
        }
        if (openCount != closeCount) {
            LoggerManager.warn("Malformed CSS comment detected in " + fileName);
        }
    }
}
