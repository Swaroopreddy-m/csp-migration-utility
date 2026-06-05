package com.csp.migration;

import com.csp.migration.config.AppConfig;
import com.csp.migration.model.ConversionReport;
import com.csp.migration.model.ConversionState;
import com.csp.migration.service.HtmlParser;
import com.csp.migration.service.UserPrompter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class CspParityValidatorTest {

    private ConversionReport report;
    private ConversionState state;
    private UserPrompter dummyPrompter;

    @BeforeEach
    public void setUp() {
        System.setProperty("csp.headless", "true");
        report = new ConversionReport();
        state = new ConversionState();
        dummyPrompter = new UserPrompter() {
            @Override
            public Choice promptMissingScript(String fileName, String fullPath) {
                return Choice.CONTINUE;
            }
            @Override
            public Choice promptMissingStyle(String fileName, String fullPath) {
                return Choice.CONTINUE;
            }
        };
    }

    @Test
    public void testFullConversionAndParity(@TempDir Path tempDir) throws Exception {
        // 1. Create subfolders
        Path srcDir = tempDir.resolve("src");
        Path jsOutDir = tempDir.resolve("out-js");
        Path cssOutDir = tempDir.resolve("out-css");
        Path poolDir = tempDir.resolve("pool");
        
        Files.createDirectories(srcDir);
        Files.createDirectories(jsOutDir);
        Files.createDirectories(cssOutDir);
        Files.createDirectories(poolDir);

        Files.createDirectories(srcDir.resolve("css"));
        Files.createDirectories(srcDir.resolve("js"));
        Files.createDirectories(poolDir.resolve("styles"));
        Files.createDirectories(poolDir.resolve("scripts"));

        // 2. Setup original files
        Path htmlFile = srcDir.resolve("index.html");
        String originalHtml = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>Manual Verification CSP Page</title>\n" +
                "    <link rel=\"stylesheet\" href=\"css/main.css\">\n" +
                "    <link rel=\"stylesheet\" href=\"css/missing-style.css\">\n" +
                "    <script src=\"js/main.js\"></script>\n" +
                "    <script src=\"js/missing-script.js\"></script>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>CSP Migration Test</h1>\n" +
                "    <!-- Priority 1 & 2: Existing ID and display:none -->\n" +
                "    <div id=\"panel1\" style=\"color: blue; display: none;\">Panel 1 Content</div>\n" +
                "    <!-- Priority 2: Existing ID + Class and display:block -->\n" +
                "    <div id=\"panel2\" class=\"box\" hidden style=\"font-weight: bold; display: block;\">Panel 2 Content</div>\n" +
                "    <!-- Priority 3: No ID, but Name exists and display:'' -->\n" +
                "    <input name=\"username\" style=\"border: 1px solid black; display: '';\" value=\"TestUser\">\n" +
                "    <!-- Priority 4: No ID/Class/Name, style -->\n" +
                "    <p style=\"margin: 15px; color: green;\">Paragraph text</p>\n" +
                "    <!-- Duplicate ID check -->\n" +
                "    <div id=\"dup-id\" style=\"padding: 10px;\">Dup 1</div>\n" +
                "    <div id=\"dup-id\" style=\"padding: 20px;\">Dup 2</div>\n" +
                "</body>\n" +
                "</html>";
        Files.writeString(htmlFile, originalHtml);

        Path mainCssFile = srcDir.resolve("css").resolve("main.css");
        String originalCss = "body {\n" +
                "    font-family: Arial, sans-serif;\n" +
                "    background-color: #f0f0f0;\n" +
                "}\n" +
                "#panel1 {\n" +
                "    font-size: 16px;\n" +
                "}\n";
        Files.writeString(mainCssFile, originalCss);

        Path mainJsFile = srcDir.resolve("js").resolve("main.js");
        String originalJs = "function init() {\n" +
                "    var myEl = document.getElementById('panel1');\n" +
                "    myEl.style.display = \"none\";\n" +
                "    otherEl.style.display = 'block';\n" +
                "    emptyEl.style.display = \"\";\n" +
                "    $(myEl).hide();\n" +
                "    $(this).show();\n" +
                "    $('#panel2').hide();\n" +
                "    $(\".box\").show();\n" +
                "}\n";
        Files.writeString(mainJsFile, originalJs);

        // Missing resource files pool
        Path missingCss = poolDir.resolve("styles").resolve("missing-style.css");
        Files.writeString(missingCss, "/* Missing style resolved recursively */\n.missing { color: purple; }");
        Path missingJs = poolDir.resolve("scripts").resolve("missing-script.js");
        Files.writeString(missingJs, "console.log('resolved missing script');");

        // 3. Pre-map the missing files in ConversionState userMappings to bypass prompts
        state.getUserMappings().put("css/missing-style.css", missingCss.toAbsolutePath().toString());
        state.getUserMappings().put("js/missing-script.js", missingJs.toAbsolutePath().toString());

        // 4. Instantiate and run App
        AppConfig config = new AppConfig(srcDir, jsOutDir, cssOutDir, "NEW_CONVERSION", "");
        App app = new App(config, state, report, dummyPrompter);
        app.run();

        // 5. Assert all expected files exist
        assertTrue(Files.exists(htmlFile));
        assertTrue(Files.exists(mainCssFile));
        assertTrue(Files.exists(mainJsFile));
        
        Path generatedCssFile = cssOutDir.resolve("index.css");
        assertTrue(Files.exists(generatedCssFile));

        // 6. Verify DOM structural and style parity
        Document doc = HtmlParser.parse(htmlFile.toFile());
        
        // #panel1 checks
        Element panel1 = doc.getElementById("panel1");
        assertNotNull(panel1);
        assertTrue(panel1.hasAttr("hidden")); // display:none -> hidden attribute
        assertFalse(panel1.hasAttr("style")); // style cleared
        
        // Assert color: blue is merged into C:\Users\...\main.css
        String mainCssContent = Files.readString(mainCssFile);
        assertTrue(mainCssContent.contains("#panel1"));
        assertTrue(mainCssContent.contains("color: blue;"));
        assertTrue(mainCssContent.contains("font-size: 16px;")); // original preserved

        // #panel2 checks
        Element panel2 = doc.getElementById("panel2");
        assertNotNull(panel2);
        assertFalse(panel2.hasAttr("hidden")); // display:block -> removed hidden attribute
        assertFalse(panel2.hasAttr("style"));
        
        // Assert font-weight: bold is in out-css/index.css
        String generatedCssContent = Files.readString(generatedCssFile);
        assertTrue(generatedCssContent.contains("#panel2"));
        assertTrue(generatedCssContent.contains("font-weight: bold;"));

        // Name username checks
        Element username = doc.getElementById("username");
        assertNotNull(username);
        assertEquals("username", username.attr("name"));
        assertFalse(username.hasAttr("style"));
        assertTrue(generatedCssContent.contains("#username"));
        assertTrue(generatedCssContent.contains("border: 1px solid black;"));

        // Priority 4: generated ID check
        Element p = doc.select("p").first();
        assertNotNull(p);
        assertTrue(p.hasAttr("id"));
        String autoId = p.attr("id");
        assertTrue(autoId.startsWith("csp_auto_"));
        assertTrue(generatedCssContent.contains("#" + autoId));
        assertTrue(generatedCssContent.contains("margin: 15px;"));
        assertTrue(generatedCssContent.contains("color: green;"));

        // Duplicate ID checks
        Elements dupIds = doc.select("#dup-id");
        assertEquals(2, dupIds.size());
        assertFalse(dupIds.get(0).hasAttr("style"));
        assertFalse(dupIds.get(1).hasAttr("style"));
        assertTrue(generatedCssContent.contains("#dup-id"));
        assertTrue(generatedCssContent.contains("padding: 20px;")); // overridden/merged
        
        // Check report metrics
        assertEquals(1, report.getDisplayNoneConversionsCount());
        assertEquals(1, report.getDisplayBlockConversionsCount());
        assertEquals(1, report.getDisplayEmptyConversionsCount());
        assertEquals(1, report.getDuplicateIdsList().size());
        assertTrue(report.getDuplicateIdsList().contains("dup-id"));

        // 7. Verify rewritten JS file contents
        String rewrittenJsContent = Files.readString(mainJsFile);
        assertTrue(rewrittenJsContent.contains("myEl.hidden = true;"));
        assertTrue(rewrittenJsContent.contains("otherEl.hidden = false;"));
        assertTrue(rewrittenJsContent.contains("emptyEl.hidden = false;"));
        assertTrue(rewrittenJsContent.contains("myEl.hidden = true;"));
        assertTrue(rewrittenJsContent.contains("this.hidden = false;"));
        assertTrue(rewrittenJsContent.contains("document.querySelector('#panel2').hidden = true;"));
        assertTrue(rewrittenJsContent.contains("document.querySelector(\".box\").hidden = false;"));
    }
}
