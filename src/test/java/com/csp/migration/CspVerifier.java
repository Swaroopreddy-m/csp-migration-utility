package com.csp.migration;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CspVerifier {
    public static List<String> verifyCspCompliance(Document doc, Path htmlFile, Path htmlRoot, String contextPath, String expectedJsName, String expectedCssName) {
        List<String> violations = new ArrayList<>();

        // 1. Audit element ID uniqueness, inline handlers, and styles
        Set<String> seenIds = new HashSet<>();
        for (Element element : doc.getAllElements()) {
            String id = element.attr("id").trim();
            if (!id.isEmpty()) {
                if (!seenIds.add(id)) {
                    violations.add("Duplicate ID found: " + id);
                }
            }

            for (Attribute attr : element.attributes()) {
                String key = attr.getKey().toLowerCase();
                if (key.startsWith("on") && key.length() > 2) {
                    violations.add("Inline event handler found on element <" + element.tagName() + "> ID '" + element.id() + "': " + attr.getKey());
                }
                if (key.equals("style")) {
                    violations.add("Inline style attribute found on element <" + element.tagName() + "> ID '" + element.id() + "': " + attr.getValue());
                }
            }
        }

        // 2. Audit script tags
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String src = script.attr("src").trim();
            if (src.isEmpty()) {
                violations.add("Inline script block found");
            } else {
                if (!src.startsWith("http://") && !src.startsWith("https://") && !src.startsWith("//")) {
                    Path resolved = ResourceResolver.resolveResource(src, htmlFile, htmlRoot, contextPath);
                    if (resolved == null || !Files.exists(resolved)) {
                        violations.add("Unresolved script resource reference: " + src);
                    }
                }
            }
        }

        // 3. Audit style tags
        Elements styles = doc.select("style");
        if (!styles.isEmpty()) {
            violations.add("Internal style block found");
        }

        // 4. Audit stylesheet links
        Elements links = doc.select("link[rel=stylesheet]");
        for (Element link : links) {
            String href = link.attr("href").trim();
            if (!href.isEmpty() && !href.startsWith("http://") && !href.startsWith("https://") && !href.startsWith("//")) {
                Path resolved = ResourceResolver.resolveResource(href, htmlFile, htmlRoot, contextPath);
                if (resolved == null || !Files.exists(resolved)) {
                    violations.add("Unresolved stylesheet resource reference: " + href);
                }
            }
        }

        // 5. Audit CSP meta header
        Elements metaCsp = doc.select("meta[http-equiv=Content-Security-Policy]");
        if (metaCsp.isEmpty()) {
            violations.add("Missing Content-Security-Policy meta header");
        }

        return violations;
    }
}
