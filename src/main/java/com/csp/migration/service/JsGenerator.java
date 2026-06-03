package com.csp.migration.service;

import com.csp.migration.model.JsBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsGenerator {

    /**
     * Generates the JS file content from a list of JsBlocks and any custom code.
     */
    public static String generateJsContent(List<JsBlock> blocks, String customCode) {
        StringBuilder sb = new StringBuilder();
        
        for (JsBlock block : blocks) {
            String metadata = getMetadataString(block);
            sb.append("/* csp_block_start:").append(metadata).append(" */").append(System.lineSeparator());
            
            if (block.getType() == JsBlock.Type.EVENT) {
                sb.append("document.addEventListener('DOMContentLoaded', () => {").append(System.lineSeparator());
                sb.append("    document.querySelectorAll('").append(block.getSelector()).append("').forEach(el => {").append(System.lineSeparator());
                sb.append("        el.addEventListener('").append(block.getEvent()).append("', function(event) {").append(System.lineSeparator());
                sb.append("            ").append(block.getContent()).append(System.lineSeparator());
                sb.append("        });").append(System.lineSeparator());
                sb.append("    });").append(System.lineSeparator());
                sb.append("});").append(System.lineSeparator());
            } else {
                sb.append(block.getContent()).append(System.lineSeparator());
            }
            
            sb.append("/* csp_block_end:").append(metadata).append(" */").append(System.lineSeparator()).append(System.lineSeparator());
        }

        if (customCode != null && !customCode.trim().isEmpty()) {
            sb.append("// === USER CUSTOM CODE ===").append(System.lineSeparator());
            sb.append(customCode.trim()).append(System.lineSeparator());
        }

        return sb.toString();
    }

    private static String getMetadataString(JsBlock block) {
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
        if (block.getEvent() != null) {
            sb.append(";event=").append(block.getEvent());
        }
        return sb.toString();
    }

    /**
     * Parses an existing JS file and separates the structured JsBlocks from custom user code.
     */
    public static DeserializedJs parseJsContent(String content) {
        List<JsBlock> blocks = new ArrayList<>();
        StringBuilder customCodeBuilder = new StringBuilder();

        if (content == null || content.isEmpty()) {
            return new DeserializedJs(blocks, "");
        }

        int currentIndex = 0;
        String startMarker = "/* csp_block_start:";
        String endMarker = "/* csp_block_end:";

        while (true) {
            int startIdx = content.indexOf(startMarker, currentIndex);
            if (startIdx == -1) {
                // Add remaining content to custom code
                String remaining = content.substring(currentIndex);
                remaining = remaining.replace("// === USER CUSTOM CODE ===", "");
                if (!remaining.trim().isEmpty()) {
                    customCodeBuilder.append(remaining);
                }
                break;
            }

            // Capture custom code before the block
            String preceding = content.substring(currentIndex, startIdx);
            preceding = preceding.replace("// === USER CUSTOM CODE ===", "");
            if (!preceding.trim().isEmpty()) {
                customCodeBuilder.append(preceding);
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

            JsBlock.Type type = JsBlock.Type.valueOf(meta.getOrDefault("type", "INTERNAL"));
            String name = meta.get("name");
            int index = Integer.parseInt(meta.getOrDefault("index", "0"));
            String selector = meta.get("selector");
            String event = meta.get("event");

            // For EVENT, we extract the inner code to avoid wrapping it again when regenerating
            if (type == JsBlock.Type.EVENT) {
                blockContent = extractEventBody(blockContent);
            }

            blocks.add(new JsBlock(type, name, index, selector, event, blockContent));

            currentIndex = endMetaEnd + 2;
        }

        return new DeserializedJs(blocks, customCodeBuilder.toString().trim());
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

    /**
     * Extracts the user's inline event handler logic from the addEventListener wrapper.
     */
    private static String extractEventBody(String wrapperContent) {
        // The block content is:
        // document.addEventListener('DOMContentLoaded', () => {
        //     document.querySelectorAll('SELECTOR').forEach(el => {
        //         el.addEventListener('EVENT', function(event) {
        //             USER_CODE
        //         });
        //     });
        // });
        // We want to find the first occurrence of `function(event) {` and matching closing braces.
        int funcIndex = wrapperContent.indexOf("function(event) {");
        if (funcIndex == -1) {
            return wrapperContent; // Fallback
        }
        int bodyStart = funcIndex + "function(event) {".length();
        // The wrapper has 3 matching closing statements at the end:
        //         });
        //     });
        // });
        // We find the last occurrences of }); and slice.
        int bodyEnd = wrapperContent.lastIndexOf("});");
        if (bodyEnd == -1 || bodyEnd <= bodyStart) {
            return wrapperContent;
        }
        bodyEnd = wrapperContent.lastIndexOf("});", bodyEnd - 1);
        if (bodyEnd == -1 || bodyEnd <= bodyStart) {
            return wrapperContent;
        }
        bodyEnd = wrapperContent.lastIndexOf("});", bodyEnd - 1);
        if (bodyEnd == -1 || bodyEnd <= bodyStart) {
            return wrapperContent;
        }
        return wrapperContent.substring(bodyStart, bodyEnd).trim();
    }

    public static class DeserializedJs {
        private final List<JsBlock> blocks;
        private final String customCode;

        public DeserializedJs(List<JsBlock> blocks, String customCode) {
            this.blocks = blocks;
            this.customCode = customCode;
        }

        public List<JsBlock> getBlocks() {
            return blocks;
        }

        public String getCustomCode() {
            return customCode;
        }
    }
}
