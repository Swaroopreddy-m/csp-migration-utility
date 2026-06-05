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
import java.util.stream.Stream;

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
    public List<ResolvedResource> resolveCssResources(Document doc, Path htmlFile, ConversionState state, ConversionReport report) {
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

            ResolvedResource res = resolveResource(ResolvedResource.Type.CSS, href, htmlFile, state, report);
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
    public List<ResolvedResource> resolveJsResources(Document doc, Path htmlFile, ConversionState state, ConversionReport report) {
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

            ResolvedResource res = resolveResource(ResolvedResource.Type.JS, src, htmlFile, state, report);
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

    private ResolvedResource resolveResource(ResolvedResource.Type type, String originalPath, Path htmlFile, ConversionState state, ConversionReport report) {
        // Try relative path resolution first
        Path relativePath = htmlFile.getParent().resolve(originalPath).normalize();
        if (Files.exists(relativePath) && Files.isRegularFile(relativePath)) {
            return new ResolvedResource(type, originalPath, relativePath, null);
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
            System.out.println("HTML File          : " + htmlFile.toAbsolutePath());
            System.out.println("Imported Resource  : " + originalPath);
            System.out.println("Attempted Location : " + relativePath.toAbsolutePath());
            System.out.println("=========================================");
            System.out.println("Resource not found.");

            report.addMissingResource(originalPath + " (in " + htmlFile.getFileName() + ")");

            Path userPath = null;
            while (userPath == null) {
                String rootFolderInput = InputManager.readLine("Please provide the root folder that contains this resource (or type 'skip'): ");
                if ("skip".equalsIgnoreCase(rootFolderInput)) {
                    report.addManualReviewWarning("User skipped missing resource: " + originalPath + " in " + htmlFile.getFileName());
                    return null;
                }

                Path rootPath = Paths.get(rootFolderInput).toAbsolutePath().normalize();
                if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
                    System.out.println("Folder does not exist or is not a directory at " + rootPath + ". Try again.");
                    continue;
                }

                // Valid folder, add to report root folders
                report.addUserRootFolder(rootPath.toString());

                // Clean up the path to extract the filename
                String cleanPath = originalPath;
                int qIdx = cleanPath.indexOf('?');
                if (qIdx != -1) {
                    cleanPath = cleanPath.substring(0, qIdx);
                }
                int hIdx = cleanPath.indexOf('#');
                if (hIdx != -1) {
                    cleanPath = cleanPath.substring(0, hIdx);
                }
                
                String filename;
                try {
                    filename = Paths.get(cleanPath).getFileName().toString();
                } catch (Exception e) {
                    String temp = cleanPath.replace("DYN_CONTEXT_PATH/", "").replace("DYN_CONTEXT_PATH", "");
                    int lastSlash = Math.max(temp.lastIndexOf('/'), temp.lastIndexOf('\\'));
                    if (lastSlash != -1) {
                        filename = temp.substring(lastSlash + 1);
                    } else {
                        filename = temp;
                    }
                }
                
                final String finalFilename = filename;

                // Recursively search the directory
                List<Path> matches = new ArrayList<>();
                try (Stream<Path> stream = Files.walk(rootPath)) {
                    stream.filter(Files::isRegularFile)
                          .forEach(p -> {
                              if (p.getFileName().toString().equalsIgnoreCase(finalFilename)) {
                                  matches.add(p);
                              }
                          });
                } catch (IOException e) {
                    System.out.println("Failed to search directory: " + e.getMessage());
                    continue;
                }

                if (matches.isEmpty()) {
                    System.out.println("No matches found in the provided root folder.");
                    String[] options = {"Provide another root folder", "Enter absolute path manually", "Skip resource"};
                    int choice = InputManager.promptMenu("No matches found. What would you like to do?", options);
                    if (choice == 2) {
                        userPath = promptManualPath(htmlFile, originalPath);
                        if (userPath != null) break;
                    } else if (choice == 3) {
                        report.addManualReviewWarning("User skipped missing resource: " + originalPath + " in " + htmlFile.getFileName());
                        return null;
                    }
                    // Otherwise choice == 1, loop again
                } else if (matches.size() == 1) {
                    userPath = matches.get(0);
                    System.out.println("Discovered resource: " + userPath.toAbsolutePath());
                    report.addDiscoveredResourcePath(userPath.toAbsolutePath().toString());
                } else {
                    // Multiple matches found, try suffix matching
                    String suffixPath = cleanPath.replace("DYN_CONTEXT_PATH/", "").replace("DYN_CONTEXT_PATH", "");
                    suffixPath = suffixPath.replace("/", java.io.File.separator).replace("\\", java.io.File.separator);
                    if (suffixPath.startsWith(java.io.File.separator)) {
                        suffixPath = suffixPath.substring(1);
                    }
                    
                    List<Path> suffixMatches = new ArrayList<>();
                    for (Path match : matches) {
                        String absPath = match.toAbsolutePath().toString();
                        if (absPath.toLowerCase().endsWith(suffixPath.toLowerCase())) {
                            suffixMatches.add(match);
                        }
                    }

                    if (suffixMatches.size() == 1) {
                        userPath = suffixMatches.get(0);
                        System.out.println("Discovered resource (suffix match): " + userPath.toAbsolutePath());
                        report.addDiscoveredResourcePath(userPath.toAbsolutePath().toString());
                    } else {
                        // Multiple matches still exist or suffix matches is empty but matches is not.
                        List<Path> candidates = suffixMatches.isEmpty() ? matches : suffixMatches;
                        String[] options = new String[candidates.size() + 2];
                        for (int i = 0; i < candidates.size(); i++) {
                            options[i] = candidates.get(i).toAbsolutePath().toString();
                        }
                        options[candidates.size()] = "Enter custom path manually";
                        options[candidates.size() + 1] = "Skip resource";

                        int choice = InputManager.promptMenu("Multiple matches found for '" + filename + "'. Please choose one:", options);
                        if (choice >= 1 && choice <= candidates.size()) {
                            userPath = candidates.get(choice - 1);
                            report.addDiscoveredResourcePath(userPath.toAbsolutePath().toString());
                        } else if (choice == candidates.size() + 1) {
                            userPath = promptManualPath(htmlFile, originalPath);
                            if (userPath != null) break;
                        } else {
                            report.addManualReviewWarning("User skipped missing resource: " + originalPath + " in " + htmlFile.getFileName());
                            return null;
                        }
                    }
                }
            }

            String userPathStr = userPath.toAbsolutePath().toString();
            state.getUserMappings().put(originalPath, userPathStr);
            report.addUserResourceMapping(originalPath, userPathStr);
            
            return new ResolvedResource(type, originalPath, userPath, userPathStr);
        }
    }

    private Path promptManualPath(Path htmlFile, String originalPath) {
        while (true) {
            String input = InputManager.readLine("Enter the absolute path to the resource (or type 'skip'): ");
            if ("skip".equalsIgnoreCase(input)) {
                return null;
            }
            Path p = Paths.get(input).toAbsolutePath().normalize();
            if (Files.exists(p) && Files.isRegularFile(p)) {
                return p;
            } else {
                System.out.println("File not found: " + p + ". Try again.");
            }
        }
    }
}
