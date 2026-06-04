package com.csp.migration;

import com.csp.migration.model.ConversionReport;
import com.csp.migration.model.CssBlock;
import com.csp.migration.service.StyleExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class StyleExtractorPriorityTest {

    private StyleExtractor styleExtractor;
    private ConversionReport report;

    @BeforeEach
    public void setUp() {
        styleExtractor = new StyleExtractor();
        report = new ConversionReport();
    }

    @Test
    public void testStylePriorityAndDisplayMigration() {
        String html = "<html><body>" +
                "  <!-- Priority 1 & 2 -->" +
                "  <div id=\"customerPanel\" class=\"btn\" style=\"color:red;\"></div>" +
                "  <!-- Priority 3 -->" +
                "  <input name=\"accountNumber\" style=\"width:200px;\">" +
                "  <!-- Priority 4 -->" +
                "  <p style=\"margin:10px;\"></p>" +
                "  <!-- Display Migration: display:none -->" +
                "  <div id=\"panel1\" style=\"display:none; color:blue;\"></div>" +
                "  <!-- Display Migration: display:block -->" +
                "  <div id=\"panel2\" hidden style=\"display:block;\"></div>" +
                "</body></html>";

        Document doc = Jsoup.parse(html);
        List<CssBlock> blocks = styleExtractor.extractInlineStyles(doc, Paths.get("test.html"), report);

        // Assert size of extracted blocks (excludes display-only block #panel2 which was completely migrated and cleaned)
        // Blocks: customerPanel, accountNumber, auto_0001, panel1 (display:none removed, color:blue remains)
        assertEquals(4, blocks.size());

        // Check Priority 1 & 2
        Element customerPanel = doc.getElementById("customerPanel");
        assertFalse(customerPanel.hasAttr("style"));
        assertEquals("#customerPanel", blocks.get(0).getSelector());
        assertEquals("color:red;", blocks.get(0).getContent());

        // Check Priority 3
        Element accountNumber = doc.getElementById("accountNumber");
        assertTrue(accountNumber.hasAttr("id"));
        assertEquals("accountNumber", accountNumber.attr("id"));
        assertFalse(accountNumber.hasAttr("style"));
        assertEquals("#accountNumber", blocks.get(1).getSelector());
        assertEquals("width:200px;", blocks.get(1).getContent());

        // Check Priority 4
        Element p = doc.select("p").first();
        assertTrue(p.hasAttr("id"));
        String autoId = p.attr("id");
        assertTrue(autoId.startsWith("csp_auto_"));
        assertFalse(p.hasAttr("style"));
        assertEquals("#" + autoId, blocks.get(2).getSelector());
        assertEquals("margin:10px;", blocks.get(2).getContent());

        // Check Display Migrations
        Element panel1 = doc.getElementById("panel1");
        assertTrue(panel1.hasAttr("hidden")); // display:none -> hidden attribute added
        assertFalse(panel1.hasAttr("style")); // remaining style (color:blue) was extracted
        assertEquals("#panel1", blocks.get(3).getSelector());
        assertEquals("color:blue;", blocks.get(3).getContent());

        Element panel2 = doc.getElementById("panel2");
        assertFalse(panel2.hasAttr("hidden")); // display:block -> hidden attribute removed
        assertFalse(panel2.hasAttr("style")); // display:block declaration removed
    }
}
