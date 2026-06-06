package com.csp.migration;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;

public class InlineStyleConverter {
    public static String convertInlineStyles(Document doc, IdGenerator idGen, boolean removeStyles) {
        StringBuilder css = new StringBuilder();
        
        // Group elements by their standardized style declaration
        Map<String, List<Element>> styleElementsMap = new LinkedHashMap<>();
        Elements styledElements = doc.select("[style]");
        for (Element element : styledElements) {
            String styleAttr = element.attr("style").trim();
            if (styleAttr.isEmpty()) {
                continue;
            }
            String standardized = standardizeStyle(styleAttr);
            List<Element> list = styleElementsMap.get(standardized);
            if (list == null) {
                list = new ArrayList<>();
                styleElementsMap.put(standardized, list);
            }
            list.add(element);
        }

        int classCounter = 1;

        // Process each style configuration
        for (Map.Entry<String, List<Element>> entry : styleElementsMap.entrySet()) {
            String standardized = entry.getKey();
            List<Element> elements = entry.getValue();

            if (elements.size() == 1) {
                // Unique Style: target by element ID
                Element element = elements.get(0);
                String id = element.attr("id").trim();
                if (id.isEmpty()) {
                    String nameAttr = element.attr("name").trim();
                    if (isUniqueName(doc, nameAttr)) {
                        id = nameAttr;
                    } else {
                        id = idGen.generateNextId();
                    }
                    element.attr("id", id);
                }

                // Generate CSS targeting #id
                css.append("#").append(id).append("{\n");
                appendDeclarations(css, standardized);
                css.append("}\n\n");

            } else {
                // Duplicate Style: target by generated class
                String className = String.format("csp_auto_style_%03d", classCounter++);
                for (Element element : elements) {
                    element.addClass(className);
                }

                // Generate CSS targeting .class
                css.append(".").append(className).append("{\n");
                appendDeclarations(css, standardized);
                css.append("}\n\n");
            }
        }

        if (removeStyles) {
            for (Element element : styledElements) {
                element.removeAttr("style");
            }
        }

        return css.toString();
    }

    private static void appendDeclarations(StringBuilder css, String standardized) {
        String[] declarations = standardized.split(";");
        for (String decl : declarations) {
            String trimmed = decl.trim();
            if (!trimmed.isEmpty()) {
                css.append("    ").append(trimmed).append(";\n");
            }
        }
    }

    private static String standardizeStyle(String styleAttr) {
        String[] parts = styleAttr.split(";");
        List<String> decls = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                decls.add(trimmed);
            }
        }
        Collections.sort(decls);
        StringBuilder sb = new StringBuilder();
        for (String d : decls) {
            sb.append(d).append(";");
        }
        return sb.toString();
    }

    private static boolean isUniqueName(Document doc, String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        if (doc.getElementById(name) != null) {
            return false;
        }
        int count = 0;
        for (Element el : doc.getAllElements()) {
            if (name.equals(el.attr("name"))) {
                count++;
            }
        }
        return count == 1;
    }
}
