package com.csp.migration;

import com.csp.migration.config.AppConfig;
import com.csp.migration.exception.ExitRequestedException;
import com.csp.migration.model.ConversionReport;
import com.csp.migration.model.ConversionState;
import com.csp.migration.service.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("      CSP Migration Utility Startup      ");
        System.out.println("=========================================");

        LoggerService.info("Application starting...");

        // Step 1: Check Recovery State
        if (CheckpointManager.checkpointExists()) {
            ConversionState state = CheckpointManager.loadCheckpoint();
            if (state != null) {
                System.out.println();
                System.out.println("Incomplete conversion detected.");
                System.out.println("Last completed phase: " + state.getPhase());
                String[] options = {"Resume Previous Execution", "Start Fresh Conversion", "Exit"};
                int choice = InputManager.promptMenu(null, options);

                if (choice == 1) {
                    resumeConversion(state);
                    return;
                } else if (choice == 2) {
                    CheckpointManager.clearCheckpoint();
                    LoggerService.info("User chose to start fresh. Cleared old checkpoint.");
                } else {
                    System.out.println("Exiting application safely.");
                    LoggerService.info("User exited on startup recovery screen.");
                    return;
                }
            }
        }

        // Step 2: Select Processing Mode
        selectProcessingMode();
    }

    private static void selectProcessingMode() {
        System.out.println();
        String[] options = {"New Conversion", "Update Existing Conversion", "Exit"};
        int choice = InputManager.promptMenu("Select Mode", options);

        if (choice == 1) {
            runNewConversion();
        } else if (choice == 2) {
            runUpdateConversion();
        } else {
            System.out.println("Exiting application safely.");
            LoggerService.info("User exited from mode selection screen.");
        }
    }

    private static void runNewConversion() {
        LoggerService.info("User selected: New Conversion Mode");
        Path htmlFolder = InputManager.getAndValidateHtmlFolder();
        Path jsFolder = InputManager.getJsOutputFolder();
        Path cssFolder = InputManager.getCssOutputFolder();
        String contextPath = InputManager.getAndValidateApplicationContextPath();

        // Check for partial conversions (existing files in target directories)
        List<Path> htmlFiles = discoverHtmlFiles(htmlFolder);
        boolean filesExist = RecoveryManager.detectPreExistingFiles(htmlFiles, jsFolder, cssFolder);

        AppConfig config = new AppConfig(htmlFolder, jsFolder, cssFolder, "NEW_CONVERSION", contextPath);
        ConversionState state = new ConversionState();
        state.setApplicationContextPath(contextPath);
        ConversionReport report = new ConversionReport();

        if (filesExist) {
            System.out.println();
            System.out.println("Existing generated files detected.");
            String[] options = {
                    "Resume Processing (skip already converted files)",
                    "Update Existing Files (merge changes)",
                    "Start Fresh (overwrite existing files)",
                    "Exit"
            };
            int choice = InputManager.promptMenu(null, options);
            if (choice == 1) {
                LoggerService.info("Recovery Rule: Resume Processing selected.");
                // Resume Processing: skip files that already have both outputs
                for (Path file : htmlFiles) {
                    String baseName = RecoveryManager.getBaseName(file);
                    if (Files.exists(jsFolder.resolve(baseName + ".js")) &&
                        Files.exists(cssFolder.resolve(baseName + ".css"))) {
                        state.getProcessedFiles().add(file.getFileName().toString());
                    }
                }
            } else if (choice == 2) {
                LoggerService.info("Recovery Rule: Update Existing Files selected.");
                config.setMode("UPDATE_CONVERSION");
            } else if (choice == 3) {
                LoggerService.info("Recovery Rule: Start Fresh selected.");
                // Overwrite files: do nothing extra
            } else {
                System.out.println("Exiting application safely.");
                LoggerService.info("User exited on recovery rule screen.");
                return;
            }
        }

        executeApp(config, state, report);
    }

    private static void runUpdateConversion() {
        LoggerService.info("User selected: Update Existing Conversion Mode");
        Path htmlLocation = InputManager.getAndValidateHtmlLocationForUpdate();
        String contextPath = InputManager.getAndValidateApplicationContextPath();

        // Discover HTML files from input
        List<Path> htmlFiles = new ArrayList<>();
        if (Files.isRegularFile(htmlLocation)) {
            htmlFiles.add(htmlLocation);
        } else {
            htmlFiles = discoverHtmlFiles(htmlLocation);
        }

        ConversionReport report = new ConversionReport();
        List<Path> filesToProcess = new ArrayList<>();

        // Process each HTML file and check for generated home.js / home.css
        for (Path file : htmlFiles) {
            Path parentDir = file.getParent();
            String baseName = RecoveryManager.getBaseName(file);
            Path jsPath = parentDir.resolve(baseName + ".js");
            Path cssPath = parentDir.resolve(baseName + ".css");

            if (Files.exists(jsPath) || Files.exists(cssPath)) {
                System.out.println();
                System.out.println("Existing generated files detected for " + file.getFileName() + ":");
                if (Files.exists(jsPath)) System.out.println("  " + jsPath.getFileName());
                if (Files.exists(cssPath)) System.out.println("  " + cssPath.getFileName());

                String[] options = {"Update Existing Files", "Skip File", "Exit"};
                int choice = InputManager.promptMenu(null, options);

                if (choice == 1) {
                    filesToProcess.add(file);
                } else if (choice == 2) {
                    LoggerService.info("User skipped file update: " + file.getFileName());
                } else {
                    System.out.println("Exiting application safely.");
                    LoggerService.info("User exited on update mode prompt.");
                    return;
                }
            } else {
                // If files do not exist, we still process it in update mode (which acts as a new conversion for this file)
                filesToProcess.add(file);
            }
        }

        if (filesToProcess.isEmpty()) {
            System.out.println("No files selected to process.");
            LoggerService.info("No files selected for update. Exiting.");
            return;
        }

        // For updates, the JS/CSS files are generated in the same directory as the HTML file
        // We will execute the App for each HTML file's directory
        for (Path file : filesToProcess) {
            Path parentDir = file.getParent();
            AppConfig config = new AppConfig(file, parentDir, parentDir, "UPDATE_CONVERSION", contextPath);
            ConversionState state = new ConversionState();
            state.setApplicationContextPath(contextPath);
            executeApp(config, state, report);
        }
    }

    private static void resumeConversion(ConversionState state) {
        LoggerService.info("Resuming conversion from checkpoint...");
        AppConfig config = new AppConfig(
                Paths.get(state.getHtmlFolder()),
                Paths.get(state.getJsFolder()),
                Paths.get(state.getCssFolder()),
                state.getMode(),
                state.getApplicationContextPath()
        );
        ConversionReport report = new ConversionReport();
        report.addGeneratedClasses(state.getProcessedFiles().size()); // Rough count
        report.addNewScriptsAdded(state.getGeneratedJsFiles().size());
        report.addNewStylesAdded(state.getGeneratedCssFiles().size());
        
        executeApp(config, state, report);
    }

    private static void executeApp(AppConfig config, ConversionState state, ConversionReport report) {
        App app = new App(config, state, report, InputManager.getCliPrompter());
        try {
            app.run();
            // Clear checkpoint on successful completion
            CheckpointManager.clearCheckpoint();
            printFinalReport(report);
        } catch (ExitRequestedException e) {
            System.out.println();
            System.out.println("Execution paused. Checkpoint saved.");
            System.out.println("You can resume the conversion next time you run the utility.");
        }
    }

    private static List<Path> discoverHtmlFiles(Path folder) {
        try (Stream<Path> stream = Files.walk(folder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".html") || p.toString().toLowerCase().endsWith(".htm"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LoggerService.error("Failed to discover HTML files in " + folder, e);
            return new ArrayList<>();
        }
    }

    private static void printFinalReport(ConversionReport report) {
        System.out.println();
        System.out.println("=========================================");
        System.out.println("CONVERSION COMPLETED SUCCESSFULLY");
        System.out.println("=========================================");
        System.out.println("HTML Files Processed     : " + report.getHtmlFilesProcessed());
        System.out.println("JS Files Generated       : " + report.getJsFilesGenerated());
        System.out.println("CSS Files Generated      : " + report.getCssFilesGenerated());
        System.out.println();
        System.out.println("New Scripts Added        : " + report.getNewScriptsAdded());
        System.out.println("New Styles Added         : " + report.getNewStylesAdded());
        System.out.println();
        System.out.println("Duplicate Scripts Removed: " + report.getDuplicateScriptsRemoved());
        System.out.println("Duplicate Styles Removed : " + report.getDuplicateStylesRemoved());
        System.out.println();
        System.out.println("Generated Classes        : " + report.getGeneratedClasses());
        System.out.println();
        System.out.println("Warnings                 : " + report.getWarnings());
        System.out.println("Errors                   : " + report.getErrors());
        System.out.println("=========================================");
        LoggerService.info("Conversion completed successfully.");
    }
}
