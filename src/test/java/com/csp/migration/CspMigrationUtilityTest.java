package com.csp.migration;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CspMigrationUtilityTest {

    @Test
    public void testIdGenerator() {
        IdGenerator gen1 = new IdGenerator("testpage");
        IdGenerator gen2 = new IdGenerator("testpage");

        String id1_1 = gen1.generateNextId();
        String id1_2 = gen1.generateNextId();
        String id2_1 = gen2.generateNextId();

        assertEquals(id1_1, id2_1);
        assertNotEquals(id1_1, id1_2);
        assertTrue(id1_1.startsWith("csp_auto_"));
    }

    @Test
    public void testDuplicateResolverScripts() {
        String extJs = "function save() { console.log('ext'); }\nvar x = 1;\nfunction other() { }";
        String intJs = "function save() { console.log('int'); }\nvar x = 2;";
        String inlineJs = "function other() { console.log('inline'); }";

        String resolved = DuplicateResolver.resolveScriptDuplicates(extJs, intJs, inlineJs);

        assertFalse(resolved.contains("console.log('ext')"));
        assertTrue(resolved.contains("console.log('int')"));
        assertTrue(resolved.contains("console.log('inline')"));
        
        assertFalse(resolved.contains("var x = 1"));
        assertTrue(resolved.contains("var x = 2"));
    }

    @Test
    public void testDuplicateResolverCss() {
        String extCss = ".box { color: red; } .other { width: 10px; }";
        String intCss = ".box { color: blue; }";
        String inlineCss = ".other { width: 20px; }";

        String resolved = DuplicateResolver.resolveCssDuplicates(extCss, intCss, inlineCss);

        Map<String, String> rules = DuplicateResolver.parseCssRules(resolved);
        assertEquals("color: blue;", rules.get(".box").trim());
        assertEquals("width: 20px;", rules.get(".other").trim());
    }

    @Test
    public void testInlineScriptConverter() {
        String html = "<html><body><button onclick=\"saveData()\">Save</button></body></html>";
        Document doc = Jsoup.parse(html);
        IdGenerator idGen = new IdGenerator("test");

        String js = InlineScriptConverter.convertInlineScripts(doc, idGen, true);

        String buttonId = doc.select("button").attr("id");
        assertTrue(buttonId.startsWith("csp_auto_"));
        assertFalse(doc.select("button").hasAttr("onclick"));

        assertTrue(js.contains("document.getElementById(\"" + buttonId + "\")"));
        assertTrue(js.contains("addEventListener(\"click\", function ()"));
        assertTrue(js.contains("saveData()"));
    }

    @Test
    public void testInlineScriptConverterWithBodyOnload() {
        String html = "<html><body onload=\"startGame()\"></body></html>";
        Document doc = Jsoup.parse(html);
        IdGenerator idGen = new IdGenerator("testbody");

        String js = InlineScriptConverter.convertInlineScripts(doc, idGen, true);

        assertFalse(doc.select("body").hasAttr("onload"));
        assertTrue(js.contains("window.addEventListener(\"load\", function ()"));
        assertTrue(js.contains("startGame()"));
        assertFalse(js.contains("document.getElementById"));
    }

    @Test
    public void testInlineScriptConverterWithNameFallback() {
        String html = "<html><body>" +
                      "<input name=\"uniqueName\" onclick=\"validate()\">" +
                      "<div id=\"takenName\"></div>" +
                      "<button name=\"takenName\" onclick=\"save()\">Click</button>" +
                      "</body></html>";
        Document doc = Jsoup.parse(html);
        IdGenerator idGen = new IdGenerator("testfallback");

        String js = InlineScriptConverter.convertInlineScripts(doc, idGen, true);

        assertEquals("uniqueName", doc.select("input").first().attr("id"));

        String btnId = doc.select("button").first().attr("id");
        assertTrue(btnId.startsWith("csp_auto_"));
        assertNotEquals("takenName", btnId);
    }

    @Test
    public void testInlineStyleConverterUniqueAndDuplicateStyles() {
        String html = "<html><body>" +
                      "<div id=\"uniqueId\" style=\"color:red\"></div>" +
                      "<p style=\"color:blue\"></p>" +
                      "<span style=\"color:blue\"></span>" +
                      "</body></html>";
        Document doc = Jsoup.parse(html);
        IdGenerator idGen = new IdGenerator("teststyle");

        String css = InlineStyleConverter.convertInlineStyles(doc, idGen, true);

        assertTrue(css.contains("#uniqueId {"));
        assertTrue(css.contains("color:red;"));

        String pId = doc.select("p").first().id();
        String spanId = doc.select("span").first().id();
        assertEquals("csp_auto_style_001", pId);
        assertEquals("csp_auto_style_002", spanId);
        assertTrue(css.contains("#csp_auto_style_001, #csp_auto_style_002 {"));
        assertTrue(css.contains("color:blue;"));
    }

    @Test
    public void testValidationManager() {
        String html = "<html><head><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none';\"></head>" +
                      "<body><script id=\"s1\" src=\"test.js\"></script></body></html>";
        String js = "function test() { console.log('hello'); }";
        String css = ".box { color: red; }";

        Path dummyPath = Paths.get("dummy.html");
        Path dummyRoot = Paths.get(".");
        
        // Write mock files so resource check passes
        try {
            Files.write(dummyRoot.resolve("test.js"), js.getBytes());
        } catch (Exception ignored) {}

        ValidationManager.ValidationResult result = ValidationManager.validate(html, dummyPath, dummyRoot, "", js, css, "test.js", "test.css");
        
        // Cleanup mock file
        try {
            Files.delete(dummyRoot.resolve("test.js"));
        } catch (Exception ignored) {}

        assertTrue(result.valid);
        assertTrue(result.errors.isEmpty());
    }

    @Test
    public void testDiffReportGenerator() {
        String original = "line1\nline2\nline3";
        String modified = "line1\nline2 changed\nline3\nline4 added";

        String diff = DiffReportGenerator.generateDiff(original, modified);
        assertTrue(diff.contains("Line 2 modified"));
        assertTrue(diff.contains("Line 4 added"));
    }
}
