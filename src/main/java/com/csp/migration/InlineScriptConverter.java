package com.csp.migration;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.ArrayList;
import java.util.List;

public class InlineScriptConverter {
    public static String convertInlineScripts(Document doc, IdGenerator idGen, boolean removeHandlers) {
        StringBuilder js = new StringBuilder();
        
        for (Element element : doc.getAllElements()) {
            List<Attribute> eventAttrs = new ArrayList<>();
            for (Attribute attr : element.attributes()) {
                String key = attr.getKey().toLowerCase();
                if (key.startsWith("on") && key.length() > 2) {
                    eventAttrs.add(attr);
                }
            }
            
            if (eventAttrs.isEmpty()) {
                continue;
            }
            
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
            
            for (Attribute attr : eventAttrs) {
                String eventName = attr.getKey().toLowerCase().substring(2);
                String handlerCode = attr.getValue().trim();
                
                if (element.tagName().equalsIgnoreCase("body") && 
                    (eventName.equals("load") || eventName.equals("unload") || eventName.equals("resize") || 
                     eventName.equals("scroll") || eventName.equals("error") || eventName.equals("beforeunload"))) {
                    js.append("window.addEventListener(\"").append(eventName).append("\", function () {\n");
                    js.append("            ").append(handlerCode).append("\n");
                    js.append("        });\n\n");
                    
                    LoggerManager.info("Inline on" + eventName + " on <body> converted for window");
                } else {
                    js.append("document.getElementById(\"").append(id).append("\")\n");
                    js.append("        .addEventListener(\"").append(eventName).append("\", function () {\n");
                    js.append("            ").append(handlerCode).append("\n");
                    js.append("        });\n\n");
                    
                    LoggerManager.info("Inline on" + eventName + " converted for element " + id);
                }
                
                if (removeHandlers) {
                    element.removeAttr(attr.getKey());
                }
            }
        }
        
        return js.toString();
    }

    private static boolean isUniqueName(Document doc, String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        return doc.getElementById(name) == null;
    }
}
