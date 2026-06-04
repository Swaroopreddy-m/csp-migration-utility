package com.csp.migration;

import com.csp.migration.service.CssMerger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CssMergerTest {

    @Test
    public void testMergeExistingSelector() {
        String originalCss = "/* Header style */\n" +
                "#header {\n" +
                "    background: blue;\n" +
                "}\n" +
                "#customerPanel {\n" +
                "    font-size: 14px;\n" +
                "    color: blue;\n" +
                "}";

        String inlineStyle = "color: red; width: 100px;";
        String merged = CssMerger.mergeStyleContent(originalCss, "#customerPanel", inlineStyle);

        // Check color overridden to red
        assertTrue(merged.contains("color: red;"));
        // Check background/font-size preserved
        assertTrue(merged.contains("font-size: 14px;"));
        assertTrue(merged.contains("background: blue;"));
        // Check new property added
        assertTrue(merged.contains("width: 100px;"));
    }

    @Test
    public void testMergeNewSelector() {
        String originalCss = "#header {\n" +
                "    background: blue;\n" +
                "}";

        String inlineStyle = "color: red;";
        String merged = CssMerger.mergeStyleContent(originalCss, "#newPanel", inlineStyle);

        // Check appended to end
        assertTrue(merged.contains("#newPanel {"));
        assertTrue(merged.contains("color: red;"));
        assertTrue(merged.contains("#header"));
    }
}
