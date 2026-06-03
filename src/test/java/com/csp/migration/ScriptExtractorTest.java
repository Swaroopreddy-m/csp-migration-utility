package com.csp.migration;

import com.csp.migration.exception.ExitRequestedException;
import com.csp.migration.exception.SkipFileException;
import com.csp.migration.model.ConversionReport;
import com.csp.migration.model.JsBlock;
import com.csp.migration.service.ClassNameGenerator;
import com.csp.migration.service.HtmlParser;
import com.csp.migration.service.ScriptExtractor;
import com.csp.migration.service.UserPrompter;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ScriptExtractorTest {

    private ScriptExtractor extractor;
    private ConversionReport report;
    private UserPrompter dummyPrompter;

    @BeforeEach
    public void setUp() {
        extractor = new ScriptExtractor();
        report = new ConversionReport();
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
    public void testExtractInternalScripts() {
        String html = "<html><body>" +
                "<script>console.log('first');</script>" +
                "<div>Some content</div>" +
                "<script>console.log('second');</script>" +
                "</body></html>";
        Document doc = HtmlParser.parse(html);
        List<JsBlock> blocks = extractor.extractInternalScripts(doc, report);

        assertEquals(2, blocks.size());
        assertEquals(JsBlock.Type.INTERNAL, blocks.get(0).getType());
        assertEquals("console.log('first');", blocks.get(0).getContent());
        assertEquals(1, blocks.get(0).getIndex());

        assertEquals(JsBlock.Type.INTERNAL, blocks.get(1).getType());
        assertEquals("console.log('second');", blocks.get(1).getContent());
        assertEquals(2, blocks.get(1).getIndex());
    }

    @Test
    public void testExtractInlineScripts() {
        String html = "<html><body>" +
                "<button id='btn1' onclick=\"alert('btn1 clicked')\">Click</button>" +
                "<a class='link' onchange=\"alert('changed')\">Link</a>" +
                "<p onblur=\"console.log('blur')\">Para</p>" +
                "</body></html>";
        Document doc = HtmlParser.parse(html);
        ClassNameGenerator classGen = new ClassNameGenerator();
        List<JsBlock> blocks = extractor.extractInlineScripts(doc, classGen, report);

        // Expecting 3 inline event handlers extracted
        assertEquals(3, blocks.size());

        // Check button (Priority 2: existing ID because no class is present)
        JsBlock b1 = blocks.stream().filter(b -> "#btn1".equals(b.getSelector())).findFirst().orElse(null);
        assertNotNull(b1);
        assertEquals("click", b1.getEvent());
        assertEquals("alert('btn1 clicked')", b1.getContent());

        // Check link (Priority 1: existing class)
        JsBlock b2 = blocks.stream().filter(b -> ".link".equals(b.getSelector())).findFirst().orElse(null);
        assertNotNull(b2);
        assertEquals("change", b2.getEvent());
        assertEquals("alert('changed')", b2.getContent());

        // Check paragraph (Priority 3: generated class because no class/ID is present)
        JsBlock b3 = blocks.stream().filter(b -> b.getSelector().startsWith(".csp_auto_")).findFirst().orElse(null);
        assertNotNull(b3);
        assertEquals("blur", b3.getEvent());
        assertEquals("console.log('blur')", b3.getContent());
        assertTrue(doc.outerHtml().contains(b3.getSelector().substring(1))); // Class was added to HTML element
    }

    @Test
    public void testExtractExternalScriptsMissingPrompt() throws IOException {
        Path tempHtml = Files.createTempFile("test", ".html");
        try {
            String html = "<html><body><script src=\"missing_script.js\"></script></body></html>";
            Document doc = HtmlParser.parse(html);

            // Test Skip option
            UserPrompter skipPrompter = new UserPrompter() {
                @Override
                public Choice promptMissingScript(String fileName, String fullPath) {
                    return Choice.SKIP_FILE;
                }
                @Override
                public Choice promptMissingStyle(String fileName, String fullPath) {
                    return Choice.CONTINUE;
                }
            };

            assertThrows(SkipFileException.class, () -> {
                extractor.extractExternalScripts(doc, tempHtml, skipPrompter, report);
            });

            // Test Exit option
            UserPrompter exitPrompter = new UserPrompter() {
                @Override
                public Choice promptMissingScript(String fileName, String fullPath) {
                    return Choice.EXIT;
                }
                @Override
                public Choice promptMissingStyle(String fileName, String fullPath) {
                    return Choice.CONTINUE;
                }
            };

            assertThrows(ExitRequestedException.class, () -> {
                extractor.extractExternalScripts(doc, tempHtml, exitPrompter, report);
            });
        } finally {
            Files.deleteIfExists(tempHtml);
        }
    }
}
