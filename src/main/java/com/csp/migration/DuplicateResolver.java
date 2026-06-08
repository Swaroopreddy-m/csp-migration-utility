package com.csp.migration;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DuplicateResolver {
    private static final Pattern FUNC_PATTERN = Pattern.compile("function\\s+([a-zA-Z0-9_$]+)\\s*\\(");
    private static final Pattern VAR_PATTERN = Pattern.compile("\\b(var|let|const)\\s+([a-zA-Z0-9_$]+)\\b");

    public static String resolveScriptDuplicates(String externalJs, String internalJs, String inlineJs) {
        // 1. Resolve function duplicates
        Set<String> externalFuncs = extractFunctionNames(externalJs);
        Set<String> internalFuncs = extractFunctionNames(internalJs);
        Set<String> inlineFuncs = extractFunctionNames(inlineJs);

        String resolvedExt = externalJs;
        for (String func : internalFuncs) {
            if (externalFuncs.contains(func)) {
                LoggerManager.warn("Duplicate function replaced by higher priority source: " + func);
                resolvedExt = removeFunctionDeclaration(resolvedExt, func);
            }
        }
        
        String resolvedInt = internalJs;
        for (String func : inlineFuncs) {
            if (internalFuncs.contains(func)) {
                LoggerManager.warn("Duplicate function replaced by higher priority source: " + func);
                resolvedInt = removeFunctionDeclaration(resolvedInt, func);
            }
            if (externalFuncs.contains(func)) {
                LoggerManager.warn("Duplicate function replaced by higher priority source: " + func);
                resolvedExt = removeFunctionDeclaration(resolvedExt, func);
            }
        }

        // 2. Resolve variable duplicates
        Set<String> externalVars = extractVariableNames(resolvedExt);
        Set<String> internalVars = extractVariableNames(resolvedInt);
        Set<String> inlineVars = extractVariableNames(inlineJs);

        for (String var : internalVars) {
            if (externalVars.contains(var)) {
                LoggerManager.warn("Duplicate variable replaced by higher priority source: " + var);
                resolvedExt = removeVariableDeclaration(resolvedExt, var);
            }
        }

        for (String var : inlineVars) {
            if (internalVars.contains(var)) {
                LoggerManager.warn("Duplicate variable replaced by higher priority source: " + var);
                resolvedInt = removeVariableDeclaration(resolvedInt, var);
            }
            if (externalVars.contains(var)) {
                LoggerManager.warn("Duplicate variable replaced by higher priority source: " + var);
                resolvedExt = removeVariableDeclaration(resolvedExt, var);
            }
        }

        StringBuilder sb = new StringBuilder();
        if (!resolvedExt.trim().isEmpty()) {
            sb.append(resolvedExt.trim()).append("\n\n");
        }
        if (!resolvedInt.trim().isEmpty()) {
            sb.append(resolvedInt.trim()).append("\n\n");
        }
        if (!inlineJs.trim().isEmpty()) {
            sb.append(inlineJs.trim()).append("\n");
        }
        return sb.toString();
    }

    public static String resolveCssDuplicates(String externalCss, String internalCss, String inlineCss) {
        Map<String, String> externalRules = parseCssRules(externalCss);
        Map<String, String> internalRules = parseCssRules(internalCss);
        Map<String, String> inlineRules = parseCssRules(inlineCss);

        for (String selector : internalRules.keySet()) {
            if (externalRules.containsKey(selector)) {
                LoggerManager.warn("Duplicate CSS selector replaced by higher priority source: " + selector);
                externalRules.remove(selector);
            }
        }

        for (String selector : inlineRules.keySet()) {
            if (internalRules.containsKey(selector)) {
                LoggerManager.warn("Duplicate CSS selector replaced by higher priority source: " + selector);
                internalRules.remove(selector);
            }
            if (externalRules.containsKey(selector)) {
                LoggerManager.warn("Duplicate CSS selector replaced by higher priority source: " + selector);
                externalRules.remove(selector);
            }
        }

        StringBuilder sb = new StringBuilder();
        appendRules(sb, externalRules);
        appendRules(sb, internalRules);
        appendRules(sb, inlineRules);
        return sb.toString();
    }

    public static Set<String> extractFunctionNames(String jsCode) {
        Set<String> names = new HashSet<>();
        if (jsCode == null || jsCode.isEmpty()) {
            return names;
        }
        String cleanCode = stripJsComments(jsCode);
        Matcher m = FUNC_PATTERN.matcher(cleanCode);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    public static Set<String> extractVariableNames(String jsCode) {
        Set<String> names = new HashSet<>();
        if (jsCode == null || jsCode.isEmpty()) {
            return names;
        }
        String cleanCode = stripJsComments(jsCode);
        Matcher m = VAR_PATTERN.matcher(cleanCode);
        while (m.find()) {
            names.add(m.group(2));
        }
        return names;
    }

    public static String stripJsComments(String jsCode) {
        String step1 = jsCode.replaceAll("/\\*(?s).*?\\*/", "");
        return step1.replaceAll("//.*", "");
    }

    private static String removeFunctionDeclaration(String jsCode, String funcName) {
        Pattern pattern = Pattern.compile("function\\s+" + Pattern.quote(funcName) + "\\s*\\(");
        Matcher m = pattern.matcher(jsCode);
        if (!m.find()) {
            return jsCode;
        }
        int startIdx = m.start();
        int openBraceIdx = jsCode.indexOf('{', startIdx);
        if (openBraceIdx == -1) {
            return jsCode;
        }
        int braceCount = 1;
        int endIdx = openBraceIdx + 1;
        while (endIdx < jsCode.length() && braceCount > 0) {
            char c = jsCode.charAt(endIdx);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
            }
            endIdx++;
        }
        return jsCode.substring(0, startIdx) + jsCode.substring(endIdx);
    }

    private static String removeVariableDeclaration(String jsCode, String varName) {
        Pattern p = Pattern.compile("\\b(var|let|const)\\s+" + Pattern.quote(varName) + "\\b");
        Matcher m = p.matcher(jsCode);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int end = m.end();
            boolean isAssignment = false;
            for (int i = end; i < jsCode.length(); i++) {
                char c = jsCode.charAt(i);
                if (c == ';') {
                    break;
                } else if (c == '=') {
                    isAssignment = true;
                    break;
                }
            }
            if (isAssignment) {
                m.appendReplacement(sb, varName);
            } else {
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static Map<String, String> parseCssRules(String cssContent) {
        Map<String, String> rules = new LinkedHashMap<>();
        if (cssContent == null || cssContent.isEmpty()) {
            return rules;
        }
        String cleanCss = cssContent.replaceAll("/\\*(?s).*?\\*/", "");
        
        int idx = 0;
        while (idx < cleanCss.length()) {
            int openBrace = cleanCss.indexOf('{', idx);
            if (openBrace == -1) {
                break;
            }
            String selector = cleanCss.substring(idx, openBrace).trim();
            
            int braceCount = 1;
            int endIdx = openBrace + 1;
            while (endIdx < cleanCss.length() && braceCount > 0) {
                char c = cleanCss.charAt(endIdx);
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                }
                endIdx++;
            }
            if (braceCount == 0) {
                String body = cleanCss.substring(openBrace + 1, endIdx - 1).trim();
                if (!selector.isEmpty()) {
                    rules.put(selector, body);
                }
            }
            idx = endIdx;
        }
        return rules;
    }

    private static void appendRules(StringBuilder sb, Map<String, String> rules) {
        for (Map.Entry<String, String> entry : rules.entrySet()) {
            sb.append(entry.getKey()).append(" {\n    ").append(entry.getValue()).append("\n}\n\n");
        }
    }
}
