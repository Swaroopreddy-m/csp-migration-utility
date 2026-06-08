package com.csp.migration;

import org.jsoup.nodes.Document;
import org.jsoup.parser.ParseError;
import org.jsoup.parser.Parser;

import javax.script.Compilable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ValidationManager {
    public static class ValidationResult {
        public boolean valid = true;
        public final List<String> errors = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
    }

    public static ValidationResult validate(String htmlContent, Path htmlFile, Path htmlRoot, String contextPath, 
                                            String jsContent, String cssContent, String jsFileName, String cssFileName) {
        ValidationResult result = new ValidationResult();

        // 1. DOM Integrity
        Parser parser = Parser.htmlParser();
        parser.setTrackErrors(100);
        Document doc = parser.parseInput(htmlContent, "");
        List<ParseError> htmlErrors = parser.getErrors();
        if (!htmlErrors.isEmpty()) {
            result.valid = false;
            for (ParseError err : htmlErrors) {
                result.errors.add("DOM error: " + err.getErrorMessage() + " at position " + err.getPosition());
            }
        }

        // 2. JS Syntax Check (Nashorn)
        if (jsContent != null && !jsContent.trim().isEmpty()) {
            try {
                ScriptEngineManager sem = new ScriptEngineManager();
                ScriptEngine engine = sem.getEngineByName("nashorn");
                if (engine instanceof Compilable) {
                    Compilable compilable = (Compilable) engine;
                    compilable.compile(jsContent);
                } else {
                    result.warnings.add("Nashorn engine not available; skipped JS syntax check.");
                }
            } catch (Exception e) {
                result.valid = false;
                result.errors.add("JavaScript syntax error: " + e.getMessage());
            }
        }

        // 3. CSS Syntax Check
        if (cssContent != null && !cssContent.trim().isEmpty()) {
            int braceCount = 0;
            boolean unbalanced = false;
            for (int i = 0; i < cssContent.length(); i++) {
                char c = cssContent.charAt(i);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                if (braceCount < 0) {
                    unbalanced = true;
                    break;
                }
            }
            if (unbalanced || braceCount != 0) {
                result.valid = false;
                result.errors.add("CSS syntax error: Unbalanced braces {} detected in stylesheet.");
            }
        }

        // 4. CSP Compliance & ID uniqueness & Resource existence Checks
        List<String> cspViolations = CspVerifier.verifyCspCompliance(doc, htmlFile, htmlRoot, contextPath, jsFileName, cssFileName);
        if (!cspViolations.isEmpty()) {
            for (String violation : cspViolations) {
                result.errors.add("Compliance Violation: " + violation);
            }
        }

        logFindings(result, jsFileName, cssFileName);
        return result;
    }

    private static void logFindings(ValidationResult result, String jsFile, String cssFile) {
        if (result.errors.isEmpty() && result.warnings.isEmpty()) {
            LoggerManager.info("Syntax, DOM, and CSP compliance validated successfully for " + jsFile + " & " + cssFile + ".");
        } else {
            for (String err : result.errors) {
                LoggerManager.error("Validation error: " + err);
            }
            for (String warn : result.warnings) {
                LoggerManager.warn("Validation warning: " + warn);
            }
        }
    }
}
