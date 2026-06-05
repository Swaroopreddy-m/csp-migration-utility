package com.csp.migration;

import com.csp.migration.config.AppConfig;
import com.csp.migration.exception.ExitRequestedException;
import com.csp.migration.exception.SkipFileException;
import com.csp.migration.model.ConversionReport;
import com.csp.migration.model.ConversionState;
import com.csp.migration.model.CssBlock;
import com.csp.migration.model.JsBlock;
import com.csp.migration.service.*;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;

public class App {
    private final AppConfig config;
    private final ConversionState state;
    private final ConversionReport report;
    private final ScriptExtractor scriptExtractor;
    private final StyleExtractor styleExtractor;
    private final UserPrompter prompter;
    
    private ExecutorService executor;
    private List<Path> htmlFiles = new ArrayList<>();
    private final List<String> skippedFiles = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean cancelRequested = false;

    public App(AppConfig config, ConversionState state, ConversionReport report, UserPrompter prompter) {
        this.config = config;
        this.state = state;
        this.report = report;
        this.prompter = prompter;
        this.scriptExtractor = new ScriptExtractor();
        this.styleExtractor = new StyleExtractor();
    }

    public void run() throws ExitRequestedException {
        LoggerService.info("Starting CSP Migration Utility in mode: " + config.getMode());
        
        // 1. Discover HTML files
        discoverHtmlFiles();
        if (htmlFiles.isEmpty()) {
            System.out.println("No HTML files found to process.");
            LoggerService.info("No HTML files found. Exiting.");
            return;
        }

        // Initialize executor
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        try {
            executePhases();
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void discoverHtmlFiles() {
        Path startPath = config.getHtmlFolder();
        if (Files.isRegularFile(startPath)) {
            htmlFiles.add(startPath);
            return;
        }
        try (Stream<Path> stream = Files.walk(startPath)) {
            htmlFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".html") || p.toString().toLowerCase().endsWith(".htm"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LoggerService.error("Failed to walk HTML folder", e);
            report.incrementErrors();
        }
    }

    private void executePhases() throws ExitRequestedException {
        String currentPhase = state.getPhase();
        if (currentPhase == null) {
            currentPhase = "RESOURCE_RESOLUTION_AND_JS_PROCESSING";
            state.setPhase(currentPhase);
            saveState();
        } else {
            // Map legacy phases to new phase names
            if (currentPhase.equals("EXTERNAL_SCRIPT_PROCESSING") ||
                currentPhase.equals("INTERNAL_SCRIPT_PROCESSING") ||
                currentPhase.equals("INLINE_SCRIPT_PROCESSING") ||
                currentPhase.equals("SCRIPT_COMPLETED")) {
                currentPhase = "RESOURCE_RESOLUTION_AND_JS_PROCESSING";
                state.setPhase(currentPhase);
                saveState();
            } else if (currentPhase.equals("EXTERNAL_STYLE_PROCESSING") ||
                       currentPhase.equals("INTERNAL_STYLE_PROCESSING") ||
                       currentPhase.equals("INLINE_STYLE_PROCESSING")) {
                currentPhase = "STYLE_PROCESSING";
                state.setPhase(currentPhase);
                saveState();
            }
        }

        while (currentPhase != null && !currentPhase.equals("COMPLETED")) {
            if (cancelRequested) {
                throw new ExitRequestedException("Process was cancelled.");
            }

            LoggerService.info("Entering phase: " + currentPhase);
            try {
                switch (currentPhase) {
                    case "RESOURCE_RESOLUTION_AND_JS_PROCESSING":
                        runParallelTask(this::processResourceResolutionAndJsProcessing);
                        transitionTo("RESOURCE_RESOLUTION_COMPLETED");
                        break;
                    case "RESOURCE_RESOLUTION_COMPLETED":
                        confirmResourceResolutionCompletion();
                        break;
                    case "STYLE_PROCESSING":
                        runParallelTask(this::processStyleProcessing);
                        transitionTo("STYLE_COMPLETED");
                        break;
                    case "STYLE_COMPLETED":
                        confirmStyleCompletion();
                        break;
                    case "HTML_GENERATION":
                        runParallelTask(this::processHtmlGeneration);
                        transitionTo("COMPLETED");
                        break;
                    default:
                        throw new IllegalStateException("Unknown phase: " + currentPhase);
                }
            } catch (ExitRequestedException e) {
                LoggerService.info("Exit requested. Checkpoint saved.");
                throw e;
            } catch (Exception e) {
                LoggerService.error("Error occurred in phase " + currentPhase, e);
                report.incrementErrors();
                saveState();
                throw new RuntimeException(e);
            }
            currentPhase = state.getPhase();
        }

        if ("COMPLETED".equals(state.getPhase())) {
            ReportWriter.writeReports(Paths.get("."), report);
        }
    }

    private void transitionTo(String nextPhase) {
        state.setPhase(nextPhase);
        saveState();
    }

    private void saveState() {
        state.setMode(config.getMode());
        state.setHtmlFolder(config.getHtmlFolder().toString());
        state.setJsFolder(config.getJsFolder().toString());
        state.setCssFolder(config.getCssFolder().toString());
        state.setWarningsCount(report.getWarnings());
        state.setErrorsCount(report.getErrors());
        CheckpointManager.saveCheckpoint(state);
    }

    private interface FileProcessor {
        void process(Path file) throws SkipFileException, ExitRequestedException, IOException;
    }

    private void runParallelTask(FileProcessor processor) throws ExitRequestedException {
        List<Future<Void>> futures = new ArrayList<>();
        
        for (Path file : htmlFiles) {
            // Check if file is skipped or already completed in previous run
            String fileName = file.getFileName().toString();
            if (skippedFiles.contains(fileName) || (state.getProcessedFiles().contains(fileName) && !state.getPhase().startsWith("HTML_GENERATION"))) {
                continue;
            }

            futures.add(executor.submit(() -> {
                if (cancelRequested) return null;
                try {
                    processor.process(file);
                } catch (SkipFileException e) {
                    LoggerService.warning("Skipping file " + fileName + ": " + e.getMessage());
                    skippedFiles.add(fileName);
                } catch (ExitRequestedException e) {
                    cancelRequested = true;
                    throw e;
                } catch (Exception e) {
                    report.incrementErrors();
                    LoggerService.error("Failed to process " + file.toAbsolutePath(), e);
                }
                return null;
            }));
        }

        // Wait for all to complete
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof ExitRequestedException) {
                    throw (ExitRequestedException) e.getCause();
                }
                LoggerService.error("Execution exception in worker thread", e);
            } catch (InterruptedException e) {
                LoggerService.error("Thread interrupted during execution", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    // --- RESOURCE RESOLUTION & JS PROCESSING ---

    private void processResourceResolutionAndJsProcessing(Path file) throws SkipFileException, ExitRequestedException, IOException {
        Document doc = HtmlParser.parse(file.toFile());
        ResourceResolver resolver = new ResourceResolver();
        List<ResourceResolver.ResolvedResource> jsResources = resolver.resolveJsResources(doc, file, state, report);
        for (ResourceResolver.ResolvedResource res : jsResources) {
            JsRewriter.rewriteJsFile(res.getResolvedPath(), report);
        }
        resolver.resolveCssResources(doc, file, state, report);
    }

    private void confirmResourceResolutionCompletion() throws ExitRequestedException {
        if ("true".equalsIgnoreCase(System.getProperty("csp.headless"))) {
            transitionTo("STYLE_PROCESSING");
            return;
        }

        System.out.println();
        System.out.println("=========================================");
        System.out.println("RESOURCE RESOLUTION & JS PROCESSING COMPLETED");
        System.out.println("=========================================");
        System.out.println("Files Processed : " + htmlFiles.size());
        System.out.println("JS Visibility Conversions : " + (report.getJsStyleDisplayConversionsCount() + report.getJsHideConversionsCount() + report.getJsShowConversionsCount()));
        System.out.println("Warnings : " + report.getWarnings());
        System.out.println("Errors : " + report.getErrors());
        System.out.println();

        String[] options = {"Continue To Style Processing", "Exit"};
        int choice = InputManager.promptMenu(null, options);
        if (choice == 2) {
            throw new ExitRequestedException("User chose to exit after resource resolution.");
        }
        transitionTo("STYLE_PROCESSING");
    }

    // --- STYLE PROCESSING METHODS ---

    private void processStyleProcessing(Path file) throws SkipFileException, ExitRequestedException, IOException {
        Document doc = HtmlParser.parse(file.toFile());
        boolean docModified = false;

        // 1. Process internal styles
        List<CssBlock> internalBlocks = styleExtractor.extractInternalStyles(doc, report);
        Path cssFile = config.getCssFolder().resolve(RecoveryManager.getBaseName(file) + ".css");
        if (!internalBlocks.isEmpty()) {
            UpdateManager.updateCssFile(cssFile, internalBlocks, report);
            addGeneratedCssFile(cssFile.getFileName().toString());
        }

        // 2. Process inline styles
        List<CssBlock> inlineBlocks = styleExtractor.extractInlineStyles(doc, file, report);
        
        // Always ensure pageName.css exists (and register it in state)
        if (!Files.exists(cssFile)) {
            FileService.writeStringTransactionally(cssFile, "");
        }
        addGeneratedCssFile(cssFile.getFileName().toString());
        
        if (!inlineBlocks.isEmpty()) {
            ResourceResolver resolver = new ResourceResolver();
            List<ResourceResolver.ResolvedResource> cssResources = resolver.resolveCssResources(doc, file, state, report);
            
            for (CssBlock block : inlineBlocks) {
                Path targetCssFile = null;
                for (ResourceResolver.ResolvedResource res : cssResources) {
                    Path path = res.getResolvedPath();
                    if (Files.exists(path)) {
                        String cssText = Files.readString(path);
                        String regex = "(?s)(?:^|\\s|\\}|,)" + Pattern.quote(block.getSelector()) + "\\s*\\{";
                        if (Pattern.compile(regex).matcher(cssText).find()) {
                            targetCssFile = path;
                            break;
                        }
                    }
                }
                if (targetCssFile == null) {
                    targetCssFile = cssFile;
                }
                CssMerger.mergeStylesIntoFile(targetCssFile, block.getSelector(), block.getContent(), report);
            }
            docModified = true;
        }

        if (docModified) {
            // Write intermediate HTML back to disk to persist generated IDs and display changes
            FileService.writeStringTransactionally(file, doc.outerHtml());
        }
    }

    private void confirmStyleCompletion() throws ExitRequestedException {
        if ("true".equalsIgnoreCase(System.getProperty("csp.headless"))) {
            transitionTo("HTML_GENERATION");
            return;
        }

        System.out.println();
        System.out.println("=========================================");
        System.out.println("STYLE EXTRACTION COMPLETED");
        System.out.println("=========================================");
        System.out.println("Files Processed : " + htmlFiles.size());
        System.out.println("CSS Files Created : " + state.getGeneratedCssFiles().size());
        System.out.println("Selectors Added : " + report.getSelectorsAdded());
        System.out.println("Selectors Merged : " + report.getSelectorsMerged());
        System.out.println("Warnings : " + report.getWarnings());
        System.out.println("Errors : " + report.getErrors());
        System.out.println();

        String[] options = {"Continue To HTML Generation", "Exit"};
        int choice = InputManager.promptMenu(null, options);
        if (choice == 2) {
            throw new ExitRequestedException("User chose to exit after style completion.");
        }
        transitionTo("HTML_GENERATION");
    }

    // --- HTML CLEANING & GENERATION ---

    private void processHtmlGeneration(Path file) throws SkipFileException, ExitRequestedException, IOException {
        Document doc = HtmlParser.parse(file.toFile());
        String baseName = RecoveryManager.getBaseName(file);
        
        String jsFileName = baseName + ".js";
        String cssFileName = baseName + ".css";

        boolean hasGeneratedJs = state.getGeneratedJsFiles().contains(jsFileName);
        boolean hasGeneratedCss = state.getGeneratedCssFiles().contains(cssFileName);

        // Call the final HTML generator to strip inline attributes/tags and insert single script/link
        HtmlGenerator.generateCspCompliantHtml(doc, file, jsFileName, cssFileName, hasGeneratedJs, hasGeneratedCss, report);
        addProcessedFile(file.getFileName().toString());
    }

    private synchronized void addGeneratedJsFile(String fileName) {
        if (!state.getGeneratedJsFiles().contains(fileName)) {
            state.getGeneratedJsFiles().add(fileName);
            report.incrementJsFilesGenerated();
        }
    }

    private synchronized void addGeneratedCssFile(String fileName) {
        if (!state.getGeneratedCssFiles().contains(fileName)) {
            state.getGeneratedCssFiles().add(fileName);
            report.incrementCssFilesGenerated();
        }
    }

    private synchronized void addProcessedFile(String fileName) {
        if (!state.getProcessedFiles().contains(fileName)) {
            state.getProcessedFiles().add(fileName);
        }
    }
}
