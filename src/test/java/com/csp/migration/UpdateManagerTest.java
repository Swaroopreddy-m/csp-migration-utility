package com.csp.migration;

import com.csp.migration.model.ConversionReport;
import com.csp.migration.model.CssBlock;
import com.csp.migration.model.JsBlock;
import com.csp.migration.service.UpdateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UpdateManagerTest {

    private ConversionReport report;
    private Path tempJs;
    private Path tempCss;

    @BeforeEach
    public void setUp() throws IOException {
        report = new ConversionReport();
        tempJs = Files.createTempFile("test_home", ".js");
        tempCss = Files.createTempFile("test_home", ".css");
    }

    @Test
    public void testUpdateJsPreservesCustomCode() throws IOException {
        // 1. Initial write with one internal block
        List<JsBlock> initialBlocks = new ArrayList<>();
        initialBlocks.add(new JsBlock(JsBlock.Type.INTERNAL, null, 1, null, null, "console.log('original');"));
        UpdateManager.updateJsFile(tempJs, initialBlocks, report);

        // 2. User edits the file and appends custom code outside markers
        String existingContent = Files.readString(tempJs);
        String modifiedContent = existingContent + "\n\n// Custom User Logic\nfunction custom() { return 42; }";
        Files.writeString(tempJs, modifiedContent);

        // 3. New scan finds one duplicate internal block and one new event block
        List<JsBlock> newScanBlocks = new ArrayList<>();
        newScanBlocks.add(new JsBlock(JsBlock.Type.INTERNAL, null, 1, null, null, "console.log('original');")); // Duplicate
        newScanBlocks.add(new JsBlock(JsBlock.Type.EVENT, null, 0, ".csp_auto_0001", "click", "alert('hello');")); // New

        UpdateManager.UpdateResult result = UpdateManager.updateJsFile(tempJs, newScanBlocks, report);

        // Verify update statistics
        assertEquals(1, result.added);
        assertEquals(1, result.skipped);

        // Verify final file content
        String finalContent = Files.readString(tempJs);
        
        // Assert new event handler is written
        assertTrue(finalContent.contains("/* csp_block_start:type=EVENT;selector=.csp_auto_0001;event=click */"));
        assertTrue(finalContent.contains("alert('hello');"));
        
        // Assert custom user logic is preserved
        assertTrue(finalContent.contains("// Custom User Logic"));
        assertTrue(finalContent.contains("function custom() { return 42; }"));
        
        // Assert original block is not duplicated
        int count = 0;
        int idx = 0;
        while ((idx = finalContent.indexOf("console.log('original');", idx)) != -1) {
            count++;
            idx += "console.log('original');".length();
        }
        assertEquals(1, count);

        // Cleanup
        Files.deleteIfExists(tempJs);
    }

    @Test
    public void testUpdateCssPreservesCustomCss() throws IOException {
        // 1. Initial CSS write
        List<CssBlock> initialBlocks = new ArrayList<>();
        initialBlocks.add(new CssBlock(CssBlock.Type.INTERNAL, null, 1, null, "body { color: black; }"));
        UpdateManager.updateCssFile(tempCss, initialBlocks, report);

        // 2. User appends custom CSS
        String existingContent = Files.readString(tempCss);
        String modifiedContent = existingContent + "\n\n/* === USER CUSTOM CSS === */\n.my-custom-class { padding: 10px; }";
        Files.writeString(tempCss, modifiedContent);

        // 3. New scan finds duplicate block and new inline block
        List<CssBlock> newScanBlocks = new ArrayList<>();
        newScanBlocks.add(new CssBlock(CssBlock.Type.INTERNAL, null, 1, null, "body { color: black; }")); // Duplicate
        newScanBlocks.add(new CssBlock(CssBlock.Type.INLINE, null, 0, ".csp_auto_0001", "margin: 20px;")); // New

        UpdateManager.UpdateResult result = UpdateManager.updateCssFile(tempCss, newScanBlocks, report);

        assertEquals(1, result.added);
        assertEquals(1, result.skipped);

        String finalContent = Files.readString(tempCss);

        // Assert new style rule is written
        assertTrue(finalContent.contains("/* csp_block_start:type=INLINE;selector=.csp_auto_0001 */"));
        assertTrue(finalContent.contains(".csp_auto_0001 {"));
        
        // Assert custom CSS is preserved
        assertTrue(finalContent.contains(".my-custom-class { padding: 10px; }"));

        // Cleanup
        Files.deleteIfExists(tempCss);
    }
}
