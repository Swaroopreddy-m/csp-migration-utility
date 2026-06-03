package com.csp.migration.service;

import com.csp.migration.model.ConversionReport;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

public class HtmlGenerator {

    /**
     * Cleans the HTML document by removing inline scripts, inline styles, internal scripts, internal styles,
     * and external stylesheet/script references, then inserts a single reference to the new generated JS and CSS files.
     * Writes the output file atomically.
     */
    public static void generateCspCompliantHtml(Document doc, Path targetHtmlPath, String jsFileName, String cssFileName, ConversionReport report)
            throws IOException {
        
        // 1. Remove all script tags (both internal and external)
        Elements scripts = doc.select("script");
        scripts.remove();

        // 2. Remove all style tags
        Elements styles = doc.select("style");
        styles.remove();

        // 3. Remove all stylesheet link tags (link rel=stylesheet)
        Elements links = doc.select("link[rel=stylesheet]");
        links.remove();

        // 4. Clean inline event handlers and inline styles from all elements
        for (Element element : doc.getAllElements()) {
            // Remove style attribute
            element.removeAttr("style");

            // Remove any attribute starting with "on"
            for (Attribute attr : new ArrayList<>(element.attributes().asList())) {
                String key = attr.getKey().toLowerCase();
                if (key.startsWith("on") && key.length() > 2) {
                    element.removeAttr(attr.getKey());
                }
            }
        }

        // 5. Append references to generated CSS and JS files in the head
        Element head = doc.head();
        if (head == null) {
            // If head is missing, prepend a head element
            head = doc.prependElement("head");
        }

        // Add CSS link
        head.appendElement("link")
                .attr("rel", "stylesheet")
                .attr("href", cssFileName);

        // Add JS script link
        head.appendElement("script")
                .attr("src", jsFileName);

        // 6. Write the cleaned HTML file atomically
        String htmlContent = doc.outerHtml();
        FileService.writeStringTransactionally(targetHtmlPath, htmlContent);
        
        report.incrementHtmlFilesProcessed();
        LoggerService.info("Generated CSP-compliant HTML at: " + targetHtmlPath.toAbsolutePath());
    }
}
