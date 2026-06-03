package com.csp.migration;

import com.csp.migration.exception.ExitRequestedException;
import com.csp.migration.exception.SkipFileException;
import com.csp.migration.model.ConversionReport;
import com.csp.migration.model.CssBlock;
import com.csp.migration.service.ClassNameGenerator;
import com.csp.migration.service.HtmlParser;
import com.csp.migration.service.StyleExtractor;
import com.csp.migration.service.UserPrompter;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StyleExtractorTest {

    private StyleExtractor extractor;
    private ConversionReport report;
    private UserPrompter dummyPrompter;

    @BeforeEach
    public void setUp() {
        extractor = new StyleExtractor();
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
    public void testExtractInternalStyles() {
        String html = "<html><body>" +
                "<style>body { background-color: #fff; }</style>" +
                "<div>Some content</div>" +
                "<style>p { color: #333; }</style>" +
                "</body></html>";
        Document doc = HtmlParser.parse(html);
        List<CssBlock> blocks = extractor.extractInternalStyles(doc, report);

        assertEquals(2, blocks.size());
        assertEquals(CssBlock.Type.INTERNAL, blocks.get(0).getType());
        assertEquals("body { background-color: #fff; }", blocks.get(0).getContent());
        assertEquals(1, blocks.get(0).getIndex());

        assertEquals(CssBlock.Type.INTERNAL, blocks.get(1).getType());
        assertEquals("p { color: #333; }", blocks.get(1).getContent());
        assertEquals(2, blocks.get(1).getIndex());
    }

    @Test
    public void testExtractInlineStyles() {
        String html = "<html><body>" +
                "<div id='div1' style='color: red; margin: 10px;'>Div 1</div>" +
                "<span class='text-span' style='font-size: 14px;'>Span 1</span>" +
                "<p style='padding: 5px;'>Para 1</p>" +
                "</body></html>";
        Document doc = HtmlParser.parse(html);
        ClassNameGenerator classGen = new ClassNameGenerator();
        List<CssBlock> blocks = extractor.extractInlineStyles(doc, classGen, report);

        assertEquals(3, blocks.size());

        // Check div (Priority 2: existing ID since no class is present)
        CssBlock b1 = blocks.stream().filter(b -> "#div1".equals(b.getSelector())).findFirst().orElse(null);
        assertNotNull(b1);
        assertEquals("color: red; margin: 10px;", b1.getContent());

        // Check span (Priority 1: existing class)
        CssBlock b2 = blocks.stream().filter(b -> ".text-span".equals(b.getSelector())).findFirst().orElse(null);
        assertNotNull(b2);
        assertEquals("font-size: 14px;", b2.getContent());

        // Check paragraph (Priority 3: generated class because no class/ID is present)
        CssBlock b3 = blocks.stream().filter(b -> b.getSelector().startsWith(".csp_auto_")).findFirst().orElse(null);
        assertNotNull(b3);
        assertEquals("padding: 5px;", b3.getContent());
        assertTrue(doc.outerHtml().contains(b3.getSelector().substring(1)));
    }

    @Test
    public void testExtractExternalStylesMissingPrompt() throws IOException {
        Path tempHtml = Files.createTempFile("test", ".html");
        try {
            String html = "<html><body><link rel=\"stylesheet\" href=\"missing_style.css\"></body></html>";
            Document doc = HtmlParser.parse(html);

            // Test Skip option
            UserPrompter skipPrompter = new UserPrompter() {
                @Override
                public Choice promptMissingScript(String fileName, String fullPath) {
                    return Choice.CONTINUE;
                }
                @Override
                public Choice promptMissingStyle(String fileName, String fullPath) {
                    return Choice.SKIP_FILE;
                }
            };

            assertThrows(SkipFileException.class, () -> {
                extractor.extractExternalStyles(doc, tempHtml, skipPrompter, report);
            });

            // Test Exit option
            UserPrompter exitPrompter = new UserPrompter() {
                @Override
                public Choice promptMissingScript(String fileName, String fullPath) {
                    return Choice.CONTINUE;
                }
                @Override
                public Choice promptMissingStyle(String fileName, String fullPath) {
                    return Choice.EXIT;
                }
            };

            assertThrows(ExitRequestedException.class, () -> {
                extractor.extractExternalStyles(doc, tempHtml, exitPrompter, report);
            });
        } finally {
            Files.deleteIfExists(tempHtml);
        }
    }
}
