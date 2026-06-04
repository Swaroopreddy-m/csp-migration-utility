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
     * Cleans the HTML document by:
     * - Removing internal script and style tags.
     * - Removing inline event handlers from all elements.
     * - Preserving external script and style references.
     * - Conditionally inserting references to the newly generated CSS/JS files.
     */
    public static void generateCspCompliantHtml(Document doc, Path targetHtmlPath, 
                                                String jsFileName, String cssFileName, 
                                                boolean hasGeneratedJs, boolean hasGeneratedCss, 
                                                ConversionReport report) throws IOException {
        
        // 1. Remove internal script tags (those without src)
        Elements internalScripts = doc.select("script:not([src])");
        internalScripts.remove();

        // 2. Remove internal style tags
        Elements internalStyles = doc.select("style");
        internalStyles.remove();

        // 3. Clean inline event handlers and remaining style attributes from all elements
        for (Element element : doc.getAllElements()) {
            // Remove remaining style attribute if empty
            if (element.hasAttr("style") && element.attr("style").trim().isEmpty()) {
                element.removeAttr("style");
            }

            // Remove any attribute starting with "on"
            for (Attribute attr : new ArrayList<>(element.attributes().asList())) {
                String key = attr.getKey().toLowerCase();
                if (key.startsWith("on") && key.length() > 2) {
                    element.removeAttr(attr.getKey());
                }
            }
        }

        // 4. Append references to generated CSS and JS files in the head if applicable
        Element head = doc.head();
        if (head == null) {
            head = doc.prependElement("head");
        }

        // Check if links are already present to avoid duplicates
        if (hasGeneratedCss) {
            boolean hasLink = doc.select("link[href=" + cssFileName + "]").size() > 0;
            if (!hasLink) {
                head.appendElement("link")
                        .attr("rel", "stylesheet")
                        .attr("href", cssFileName);
            }
        }

        if (hasGeneratedJs) {
            boolean hasScript = doc.select("script[src=" + jsFileName + "]").size() > 0;
            if (!hasScript) {
                head.appendElement("script")
                        .attr("src", jsFileName);
            }
        }

        // 5. Write the cleaned HTML file atomically
        String htmlContent = doc.outerHtml();
        FileService.writeStringTransactionally(targetHtmlPath, htmlContent);
        
        report.incrementHtmlFilesProcessed();
        report.addHtmlFile(targetHtmlPath.getFileName().toString());
        LoggerService.info("Generated CSP-compliant HTML at: " + targetHtmlPath.toAbsolutePath());
    }
}
