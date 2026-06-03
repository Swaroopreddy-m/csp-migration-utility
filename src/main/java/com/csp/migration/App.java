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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            currentPhase = "EXTERNAL_SCRIPT_PROCESSING";
            state.setPhase(currentPhase);
            saveState();
        }

        while (currentPhase != null && !currentPhase.equals("COMPLETED")) {
            if (cancelRequested) {
                throw new ExitRequestedException("Process was cancelled.");
            }

            LoggerService.info("Entering phase: " + currentPhase);
            try {
                switch (currentPhase) {
                    case "EXTERNAL_SCRIPT_PROCESSING":
                        runParallelTask(this::processExternalScripts);
                        transitionTo("INTERNAL_SCRIPT_PROCESSING");
                        break;
                    case "INTERNAL_SCRIPT_PROCESSING":
                        runParallelTask(this::processInternalScripts);
                        transitionTo("INLINE_SCRIPT_PROCESSING");
                        break;
                    case "INLINE_SCRIPT_PROCESSING":
                        runParallelTask(this::processInlineScripts);
                        transitionTo("SCRIPT_COMPLETED");
                        break;
                    case "SCRIPT_COMPLETED":
                        confirmScriptCompletion();
                        break;
                    case "EXTERNAL_STYLE_PROCESSING":
                        runParallelTask(this::processExternalStyles);
                        transitionTo("INTERNAL_STYLE_PROCESSING");
                        break;
                    case "INTERNAL_STYLE_PROCESSING":
                        runParallelTask(this::processInternalStyles);
                        transitionTo("INLINE_STYLE_PROCESSING");
                        break;
                    case "INLINE_STYLE_PROCESSING":
                        runParallelTask(this::processInlineStyles);
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

    // --- SCRIPT EXTRACTION METHODS ---

    private void processExternalScripts(Path file) throws SkipFileException, ExitRequestedException, IOException {
        Document doc = HtmlParser.parse(file.toFile());
        List<JsBlock> blocks = scriptExtractor.extractExternalScripts(doc, file, prompter, report);
        if (!blocks.isEmpty()) {
            Path jsFile = config.getJsFolder().resolve(RecoveryManager.getBaseName(file) + ".js");
            UpdateManager.updateJsFile(jsFile, blocks, report);
            addGeneratedJsFile(jsFile.getFileName().toString());
        }
    }

    private void processInternalScripts(Path file) throws SkipFileException, ExitRequestedException, IOException {
        Document doc = HtmlParser.parse(file.toFile());
        List<JsBlock> blocks = scriptExtractor.extractInternalScripts(doc, report);
        if (!blocks.isEmpty()) {
            Path jsFile = config.getJsFolder().resolve(RecoveryManager.getBaseName(file) + ".js");
            UpdateManager.updateJsFile(jsFile, blocks, report);
            addGeneratedJsFile(jsFile.getFileName().toString());
        }
    }

    private void processInlineScripts(Path file) throws SkipFileException, ExitRequestedException, IOException {
        Document doc = HtmlParser.parse(file.toFile());
        ClassNameGenerator classGen = new ClassNameGenerator();
        // Scan HTML for existing classes to avoid conflicts
        classGen.scanExistingClasses(doc.html());
        
        List<JsBlock> blocks = scriptExtractor.extractInlineScripts(doc, classGen, report);
        if (!blocks.isEmpty()) {
            Path jsFile = config.getJsFolder().resolve(RecoveryManager.getBaseName(file) + ".js");
            UpdateManager.updateJsFile(jsFile, blocks, report);
            addGeneratedJsFile(jsFile.getFileName().toString());
            
            // Write the intermediate HTML back to disk to persist the generated class names
            FileService.writeStringTransactionally(file, doc.outerHtml());
        }
    }

    private void confirmScriptCompletion() throws ExitRequestedException {
        System.out.println();
        System.out.println("=========================================");
        System.out.println("SCRIPT EXTRACTION COMPLETED");
        System.out.println("=========================================");
        System.out.println("Files Processed : " + htmlFiles.size());
        System.out.println("JS Files Created : " + state.getGeneratedJsFiles().size());
        System.out.println("Warnings : " + report.getWarnings());
        System.out.println("Errors : " + report.getErrors());
        System.out.println();

        String[] options = {"Continue To Style Processing", "Exit"};
        int choice = InputManager.promptMenu(null, options);
        if (choice == 2) {
            throw new ExitRequestedException("User chose to exit after script completion.");
        }
        transitionTo("EXTERNAL_STYLE_PROCESSING");
    }

    // --- STYLE EXTRACTION METHODS ---

    private void processExternalStyles(Path file) throws SkipFileException, ExitRequestedException, IOException {
        Document doc = HtmlParser.parse(file.toFile());
        List<CssBlock> blocks = styleExtractor.extractExternalStyles(doc, file, prompter, report);
        if (!blocks.isEmpty()) {
            Path cssFile = config.getCssFolder().resolve(RecoveryManager.getBaseName(file) + ".css");
            UpdateManager.updateCssFile(cssFile, blocks, report);
            addGeneratedCssFile(cssFile.getFileName().toString());
        }
    }

    private void processInternalStyles(Path file) throws SkipFileException, ExitRequestedException, IOException {
        Document doc = HtmlParser.parse(file.toFile());
        List<CssBlock> blocks = styleExtractor.extractInternalStyles(doc, report);
        if (!blocks.isEmpty()) {
            Path cssFile = config.getCssFolder().resolve(RecoveryManager.getBaseName(file) + ".css");
            UpdateManager.updateCssFile(cssFile, blocks, report);
            addGeneratedCssFile(cssFile.getFileName().toString());
        }
    }

    private void processInlineStyles(Path file) throws SkipFileException, ExitRequestedException, IOException {
        Document doc = HtmlParser.parse(file.toFile());
        ClassNameGenerator classGen = new ClassNameGenerator();
        classGen.scanExistingClasses(doc.html());

        List<CssBlock> blocks = styleExtractor.extractInlineStyles(doc, classGen, report);
        if (!blocks.isEmpty()) {
            Path cssFile = config.getCssFolder().resolve(RecoveryManager.getBaseName(file) + ".css");
            UpdateManager.updateCssFile(cssFile, blocks, report);
            addGeneratedCssFile(cssFile.getFileName().toString());

            // Write intermediate HTML back to disk to persist generated class names
            FileService.writeStringTransactionally(file, doc.outerHtml());
        }
    }

    private void confirmStyleCompletion() throws ExitRequestedException {
        System.out.println();
        System.out.println("=========================================");
        System.out.println("STYLE EXTRACTION COMPLETED");
        System.out.println("=========================================");
        System.out.println("Files Processed : " + htmlFiles.size());
        System.out.println("CSS Files Created : " + state.getGeneratedCssFiles().size());
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

        // Call the final HTML generator to strip inline attributes/tags and insert single script/link
        HtmlGenerator.generateCspCompliantHtml(doc, file, jsFileName, cssFileName, report);
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
