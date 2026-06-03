package com.csp.migration.service;

import com.csp.migration.model.CssBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CssGenerator {

    /**
     * Generates the CSS file content from a list of CssBlocks and any custom code.
     */
    public static String generateCssContent(List<CssBlock> blocks, String customCode) {
        StringBuilder sb = new StringBuilder();

        for (CssBlock block : blocks) {
            String metadata = getMetadataString(block);
            sb.append("/* csp_block_start:").append(metadata).append(" */").append(System.lineSeparator());

            if (block.getType() == CssBlock.Type.INLINE) {
                sb.append(block.getSelector()).append(" {").append(System.lineSeparator());
                sb.append("    ").append(block.getContent()).append(System.lineSeparator());
                sb.append("}").append(System.lineSeparator());
            } else {
                sb.append(block.getContent()).append(System.lineSeparator());
            }

            sb.append("/* csp_block_end:").append(metadata).append(" */").append(System.lineSeparator()).append(System.lineSeparator());
        }

        if (customCode != null && !customCode.trim().isEmpty()) {
            sb.append("/* === USER CUSTOM CSS === */").append(System.lineSeparator());
            sb.append(customCode.trim()).append(System.lineSeparator());
        }

        return sb.toString();
    }

    private static String getMetadataString(CssBlock block) {
        StringBuilder sb = new StringBuilder();
        sb.append("type=").append(block.getType().name());
        if (block.getName() != null) {
            sb.append(";name=").append(block.getName());
        }
        if (block.getIndex() > 0) {
            sb.append(";index=").append(block.getIndex());
        }
        if (block.getSelector() != null) {
            sb.append(";selector=").append(block.getSelector());
        }
        return sb.toString();
    }

    /**
     * Parses an existing CSS file and separates the structured CssBlocks from custom user CSS.
     */
    public static DeserializedCss parseCssContent(String content) {
        List<CssBlock> blocks = new ArrayList<>();
        StringBuilder customCssBuilder = new StringBuilder();

        if (content == null || content.isEmpty()) {
            return new DeserializedCss(blocks, "");
        }

        int currentIndex = 0;
        String startMarker = "/* csp_block_start:";
        String endMarker = "/* csp_block_end:";

        while (true) {
            int startIdx = content.indexOf(startMarker, currentIndex);
            if (startIdx == -1) {
                // Add remaining content to custom CSS
                String remaining = content.substring(currentIndex);
                remaining = remaining.replace("/* === USER CUSTOM CSS === */", "");
                if (!remaining.trim().isEmpty()) {
                    customCssBuilder.append(remaining);
                }
                break;
            }

            // Capture custom CSS before the block
            String preceding = content.substring(currentIndex, startIdx);
            preceding = preceding.replace("/* === USER CUSTOM CSS === */", "");
            if (!preceding.trim().isEmpty()) {
                customCssBuilder.append(preceding);
            }

            int startMetaEnd = content.indexOf("*/", startIdx);
            if (startMetaEnd == -1) {
                break;
            }

            String metadataStr = content.substring(startIdx + startMarker.length(), startMetaEnd).trim();
            Map<String, String> meta = parseMetadata(metadataStr);

            int endIdx = content.indexOf(endMarker, startMetaEnd);
            if (endIdx == -1) {
                break;
            }

            int endMetaEnd = content.indexOf("*/", endIdx);
            if (endMetaEnd == -1) {
                break;
            }

            String blockContent = content.substring(startMetaEnd + 2, endIdx).trim();

            CssBlock.Type type = CssBlock.Type.valueOf(meta.getOrDefault("type", "INTERNAL"));
            String name = meta.get("name");
            int index = Integer.parseInt(meta.getOrDefault("index", "0"));
            String selector = meta.get("selector");

            if (type == CssBlock.Type.INLINE) {
                blockContent = extractInlineDeclarations(blockContent);
            }

            blocks.add(new CssBlock(type, name, index, selector, blockContent));

            currentIndex = endMetaEnd + 2;
        }

        return new DeserializedCss(blocks, customCssBuilder.toString().trim());
    }

    private static Map<String, String> parseMetadata(String metaStr) {
        Map<String, String> map = new HashMap<>();
        String[] parts = metaStr.split(";");
        for (String part : parts) {
            String[] kv = part.split("=");
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }

    private static String extractInlineDeclarations(String wrapperContent) {
        int braceOpen = wrapperContent.indexOf("{");
        int braceClose = wrapperContent.lastIndexOf("}");
        if (braceOpen != -1 && braceClose != -1 && braceClose > braceOpen) {
            return wrapperContent.substring(braceOpen + 1, braceClose).trim();
        }
        return wrapperContent;
    }

    public static class DeserializedCss {
        private final List<CssBlock> blocks;
        private final String customCss;

        public DeserializedCss(List<CssBlock> blocks, String customCss) {
            this.blocks = blocks;
            this.customCss = customCss;
        }

        public List<CssBlock> getBlocks() {
            return blocks;
        }

        public String getCustomCss() {
            return customCss;
        }
    }
}
