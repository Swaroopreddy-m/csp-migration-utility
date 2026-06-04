package com.csp.migration.service;

import com.csp.migration.model.ConversionReport;
import com.csp.migration.model.ConversionState;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ResourceResolver {

    public static class ResolvedResource {
        public enum Type { CSS, JS }
        private final Type type;
        private final String originalPath;
        private final Path resolvedPath;
        private final String userProvidedPath;

        public ResolvedResource(Type type, String originalPath, Path resolvedPath, String userProvidedPath) {
            this.type = type;
            this.originalPath = originalPath;
            this.resolvedPath = resolvedPath;
            this.userProvidedPath = userProvidedPath;
        }

        public Type getType() {
            return type;
        }

        public String getOriginalPath() {
            return originalPath;
        }

        public Path getResolvedPath() {
            return resolvedPath;
        }

        public String getUserProvidedPath() {
            return userProvidedPath;
        }
    }

    /**
     * Resolves the imported CSS stylesheets in the HTML document.
     */
    public List<ResolvedResource> resolveCssResources(Document doc, Path htmlFile, String contextPath, ConversionState state, ConversionReport report) {
        List<ResolvedResource> resolvedList = new ArrayList<>();
        Elements links = doc.select("link[rel=stylesheet], link[href$=.css]");

        for (Element link : links) {
            String href = link.attr("href").trim();
            if (href.isEmpty() || isRemote(href)) {
                continue;
            }

            // Skip self reference
            String targetCssName = RecoveryManager.getBaseName(htmlFile) + ".css";
            if (href.equalsIgnoreCase(targetCssName)) {
                continue;
            }

            ResolvedResource res = resolveResource(ResolvedResource.Type.CSS, href, htmlFile, contextPath, state, report);
            if (res != null) {
                resolvedList.add(res);
                report.addCssFile(res.getResolvedPath().toAbsolutePath().toString());
            }
        }
        return resolvedList;
    }

    /**
     * Resolves the imported scripts in the HTML document.
     */
    public List<ResolvedResource> resolveJsResources(Document doc, Path htmlFile, String contextPath, ConversionState state, ConversionReport report) {
        List<ResolvedResource> resolvedList = new ArrayList<>();
        Elements scripts = doc.select("script[src]");

        for (Element script : scripts) {
            String src = script.attr("src").trim();
            if (src.isEmpty() || isRemote(src)) {
                continue;
            }

            // Skip self reference
            String targetJsName = RecoveryManager.getBaseName(htmlFile) + ".js";
            if (src.equalsIgnoreCase(targetJsName)) {
                continue;
            }

            ResolvedResource res = resolveResource(ResolvedResource.Type.JS, src, htmlFile, contextPath, state, report);
            if (res != null) {
                resolvedList.add(res);
                report.addJsFile(res.getResolvedPath().toAbsolutePath().toString());
            }
        }
        return resolvedList;
    }

    private boolean isRemote(String path) {
        return path.startsWith("http://") || path.startsWith("https://") || path.startsWith("//");
    }

    private ResolvedResource resolveResource(ResolvedResource.Type type, String originalPath, Path htmlFile, String contextPath, ConversionState state, ConversionReport report) {
        // Try relative path resolution first
        Path relativePath = htmlFile.getParent().resolve(originalPath).normalize();
        if (Files.exists(relativePath) && Files.isRegularFile(relativePath)) {
            return new ResolvedResource(type, originalPath, relativePath, null);
        }

        // Try replacing DYN_CONTEXT_PATH
        if (originalPath.contains("DYN_CONTEXT_PATH")) {
            String ctx = contextPath != null ? contextPath : "";
            String replacedStr = originalPath.replace("DYN_CONTEXT_PATH", ctx);
            Path replacedPath = Paths.get(replacedStr).normalize();
            if (Files.exists(replacedPath) && Files.isRegularFile(replacedPath)) {
                return new ResolvedResource(type, originalPath, replacedPath, null);
            }
        }

        // Try using existing mapped path from state
        if (state.getUserMappings().containsKey(originalPath)) {
            String userPathStr = state.getUserMappings().get(originalPath);
            Path userPath = Paths.get(userPathStr).normalize();
            if (Files.exists(userPath) && Files.isRegularFile(userPath)) {
                return new ResolvedResource(type, originalPath, userPath, userPathStr);
            }
        }

        // Prompt the user (thread-safe, synchronized)
        synchronized (InputManager.class) {
            // Re-check map in case another thread mapped it while we were waiting
            if (state.getUserMappings().containsKey(originalPath)) {
                String userPathStr = state.getUserMappings().get(originalPath);
                Path userPath = Paths.get(userPathStr).normalize();
                if (Files.exists(userPath) && Files.isRegularFile(userPath)) {
                    return new ResolvedResource(type, originalPath, userPath, userPathStr);
                }
            }

            System.out.println();
            System.out.println("=========================================");
            System.out.println("MISSING RESOURCE DETECTED");
            System.out.println("=========================================");
            System.out.println("HTML File     : " + htmlFile.toAbsolutePath());
            System.out.println("Original Path : " + originalPath);
            
            String replacedStr = null;
            if (originalPath.contains("DYN_CONTEXT_PATH")) {
                String ctx = contextPath != null ? contextPath : "";
                replacedStr = originalPath.replace("DYN_CONTEXT_PATH", ctx);
                System.out.println("Replaced Path : " + replacedStr);
            }

            report.addMissingResource(originalPath + " (in " + htmlFile.getFileName() + ")");

            Path userPath = null;
            while (userPath == null) {
                String input = InputManager.readLine("Provide correct file path (or type 'skip'): ");
                if ("skip".equalsIgnoreCase(input)) {
                    report.addManualReviewWarning("User skipped missing resource: " + originalPath + " in " + htmlFile.getFileName());
                    return null;
                }
                
                Path p = Paths.get(input).normalize();
                if (Files.exists(p) && Files.isRegularFile(p)) {
                    userPath = p;
                } else {
                    // Try resolving relative to HTML if it's relative
                    Path pr = htmlFile.getParent().resolve(input).normalize();
                    if (Files.exists(pr) && Files.isRegularFile(pr)) {
                        userPath = pr;
                    } else {
                        System.out.println("File not found: " + p.toAbsolutePath() + ". Try again.");
                    }
                }
            }

            String userPathStr = userPath.toAbsolutePath().toString();
            state.getUserMappings().put(originalPath, userPathStr);
            report.addUserResourceMapping(originalPath, userPathStr);
            
            return new ResolvedResource(type, originalPath, userPath, userPathStr);
        }
    }
}
