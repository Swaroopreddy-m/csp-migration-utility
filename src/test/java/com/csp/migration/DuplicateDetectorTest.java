package com.csp.migration;

import com.csp.migration.model.ConversionReport;
import com.csp.migration.model.CssBlock;
import com.csp.migration.model.JsBlock;
import com.csp.migration.service.DuplicateDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DuplicateDetectorTest {

    private ConversionReport report;

    @BeforeEach
    public void setUp() {
        report = new ConversionReport();
    }

    @Test
    public void testFilterDuplicateJsBlocks() {
        List<JsBlock> blocks = new ArrayList<>();
        blocks.add(new JsBlock(JsBlock.Type.INTERNAL, null, 1, null, null, "console.log('test');"));
        // Duplicate internal
        blocks.add(new JsBlock(JsBlock.Type.INTERNAL, null, 2, null, null, "  console.log('test');  "));
        
        blocks.add(new JsBlock(JsBlock.Type.IMPORTED, "common.js", 0, null, null, "var a = 1;"));
        // Duplicate imported (same name & content)
        blocks.add(new JsBlock(JsBlock.Type.IMPORTED, "common.js", 0, null, null, "var a = 1;"));
        
        blocks.add(new JsBlock(JsBlock.Type.EVENT, null, 0, ".btn", "click", "doAction();"));
        // Duplicate event listener (same selector, event, and code)
        blocks.add(new JsBlock(JsBlock.Type.EVENT, null, 0, ".btn", "click", "doAction();"));
        
        // Different event listener
        blocks.add(new JsBlock(JsBlock.Type.EVENT, null, 0, ".btn", "mouseover", "doAction();"));

        List<JsBlock> unique = DuplicateDetector.filterDuplicateJsBlocks(blocks, report);

        assertEquals(4, unique.size());
        assertEquals(3, report.getDuplicateScriptsRemoved());
    }

    @Test
    public void testFilterDuplicateCssBlocks() {
        List<CssBlock> blocks = new ArrayList<>();
        blocks.add(new CssBlock(CssBlock.Type.INTERNAL, null, 1, null, "body { color: black; }"));
        // Duplicate internal
        blocks.add(new CssBlock(CssBlock.Type.INTERNAL, null, 2, null, "body { color: black; }"));
        
        blocks.add(new CssBlock(CssBlock.Type.INLINE, null, 0, ".box", "color: red;"));
        // Duplicate inline rule
        blocks.add(new CssBlock(CssBlock.Type.INLINE, null, 0, ".box", "color: red;"));
        
        // Different declarations on same selector (not duplicates)
        blocks.add(new CssBlock(CssBlock.Type.INLINE, null, 0, ".box", "margin: 10px;"));

        List<CssBlock> unique = DuplicateDetector.filterDuplicateCssBlocks(blocks, report);

        assertEquals(3, unique.size());
        assertEquals(2, report.getDuplicateStylesRemoved());
    }
}
