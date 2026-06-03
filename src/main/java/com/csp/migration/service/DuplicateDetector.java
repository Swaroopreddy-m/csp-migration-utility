package com.csp.migration.service;

import com.csp.migration.model.ConversionReport;
import com.csp.migration.model.CssBlock;
import com.csp.migration.model.JsBlock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DuplicateDetector {

    /**
     * Filters duplicate JS blocks, maintaining insertion order.
     * Logs all duplicate removals to warning.log and updates the report.
     */
    public static List<JsBlock> filterDuplicateJsBlocks(List<JsBlock> blocks, ConversionReport report) {
        List<JsBlock> uniqueBlocks = new ArrayList<>();
        Set<JsBlock> seen = new LinkedHashSet<>();

        for (JsBlock block : blocks) {
            if (seen.contains(block)) {
                report.addDuplicateScriptsRemoved(1);
                String detail = getJsBlockDetail(block);
                LoggerService.warning("Removed duplicate JS block: " + detail);
            } else {
                seen.add(block);
                uniqueBlocks.add(block);
            }
        }
        return uniqueBlocks;
    }

    /**
     * Filters duplicate CSS blocks, maintaining insertion order.
     * Logs all duplicate removals to warning.log and updates the report.
     */
    public static List<CssBlock> filterDuplicateCssBlocks(List<CssBlock> blocks, ConversionReport report) {
        List<CssBlock> uniqueBlocks = new ArrayList<>();
        Set<CssBlock> seen = new LinkedHashSet<>();

        for (CssBlock block : blocks) {
            if (seen.contains(block)) {
                report.addDuplicateStylesRemoved(1);
                String detail = getCssBlockDetail(block);
                LoggerService.warning("Removed duplicate CSS rule: " + detail);
            } else {
                seen.add(block);
                uniqueBlocks.add(block);
            }
        }
        return uniqueBlocks;
    }

    private static String getJsBlockDetail(JsBlock block) {
        switch (block.getType()) {
            case IMPORTED:
                return "Imported script [src=" + block.getName() + "]";
            case INTERNAL:
                return "Internal script block [index=" + block.getIndex() + "]";
            case EVENT:
                return "Event listener [selector=" + block.getSelector() + ", event=" + block.getEvent() + "]";
            default:
                return "Unknown JS block";
        }
    }

    private static String getCssBlockDetail(CssBlock block) {
        switch (block.getType()) {
            case IMPORTED:
                return "Imported stylesheet [href=" + block.getName() + "]";
            case INTERNAL:
                return "Internal style block [index=" + block.getIndex() + "]";
            case INLINE:
                return "Inline style rule [selector=" + block.getSelector() + "]";
            default:
                return "Unknown CSS block";
        }
    }
}
