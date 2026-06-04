package com.csp.migration;

import com.csp.migration.model.ConversionReport;
import com.csp.migration.service.JsRewriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsRewriterTest {

    private ConversionReport report;

    @BeforeEach
    public void setUp() {
        report = new ConversionReport();
    }

    @Test
    public void testRewriteJsVisibility(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("test.js");
        String originalContent = "function toggle() {\n" +
                "    var myDiv = document.getElementById('myDiv');\n" +
                "    myDiv.style.display = 'none';\n" +
                "    otherDiv.style.display = \"block\";\n" +
                "    emptyDiv.style.display = \"\";\n" +
                "    $(myButton).hide();\n" +
                "    $(this).show();\n" +
                "    // Complex visibility warnings below\n" +
                "    myDiv.style.visibility = 'hidden';\n" +
                "    $(myDiv).css('display', 'none');\n" +
                "    $(myDiv).toggle();\n" +
                "}";

        Files.writeString(tempFile, originalContent);

        JsRewriter.rewriteJsFile(tempFile, report);

        String updatedContent = Files.readString(tempFile);

        // Assert visibility updates
        assertTrue(updatedContent.contains("myDiv.hidden = true;"));
        assertTrue(updatedContent.contains("otherDiv.hidden = false;"));
        assertTrue(updatedContent.contains("emptyDiv.hidden = false;"));
        assertTrue(updatedContent.contains("myButton.hidden = true;"));
        assertTrue(updatedContent.contains("this.hidden = false;"));

        // Assert display properties were removed/replaced
        assertFalse(updatedContent.contains("style.display = 'none'"));
        assertFalse(updatedContent.contains("style.display = \"block\""));
        assertFalse(updatedContent.contains("$(myButton).hide()"));

        // Assert report metrics
        assertEquals(5, report.getJsVisibilityConversions().size());
        assertEquals(3, report.getManualReviewWarnings().size());
        assertEquals(3, report.getWarnings());
    }
}
