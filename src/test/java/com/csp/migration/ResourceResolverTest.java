package com.csp.migration;

import com.csp.migration.model.ConversionReport;
import com.csp.migration.model.ConversionState;
import com.csp.migration.service.ResourceResolver;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceResolverTest {

    private ResourceResolver resolver;
    private ConversionState state;
    private ConversionReport report;

    @BeforeEach
    public void setUp() {
        resolver = new ResourceResolver();
        state = new ConversionState();
        report = new ConversionReport();
    }

    @Test
    public void testResolveLocalAndContextPathResources(@TempDir Path tempDir) throws IOException {
        // Setup directories and files
        Path htmlFile = tempDir.resolve("index.html");
        Path localCssFile = tempDir.resolve("local.css");
        Files.writeString(localCssFile, "body { background: white; }");

        // Write HTML file
        String html = "<html><head>" +
                "  <link rel=\"stylesheet\" href=\"local.css\">" +
                "  <script src=\"missing-mapped.js\"></script>" +
                "</head><body></body></html>";
        Files.writeString(htmlFile, html);

        // Pre-map a missing script file in ConversionState
        Path missingJsFile = tempDir.resolve("mapped.js");
        Files.writeString(missingJsFile, "console.log('mapped');");
        state.getUserMappings().put("missing-mapped.js", missingJsFile.toAbsolutePath().toString());

        Document doc = Jsoup.parse(html);

        // Test CSS Resolution
        List<ResourceResolver.ResolvedResource> cssResources = resolver.resolveCssResources(
                doc, htmlFile, state, report
        );

        assertEquals(1, cssResources.size());
        assertEquals("local.css", cssResources.get(0).getOriginalPath());
        assertEquals(localCssFile.toAbsolutePath().normalize(), cssResources.get(0).getResolvedPath().toAbsolutePath().normalize());

        // Test JS Resolution (resolves via User Mappings in State)
        List<ResourceResolver.ResolvedResource> jsResources = resolver.resolveJsResources(
                doc, htmlFile, state, report
        );

        assertEquals(1, jsResources.size());
        assertEquals("missing-mapped.js", jsResources.get(0).getOriginalPath());
        assertEquals(missingJsFile.toAbsolutePath().normalize(), jsResources.get(0).getResolvedPath().toAbsolutePath().normalize());
    }
}
