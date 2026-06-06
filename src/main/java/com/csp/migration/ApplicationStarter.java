package com.csp.migration;

import com.csp.migration.exception.ExitRequestedException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ApplicationStarter {
    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("      Java 8 Strict CSP Migration Utility");
        System.out.println("=========================================");
        
        LoggerManager.info("Application starting...");
        ResourceResolver.reset();

        try {
            Path htmlRoot = InputManager.getAndValidateHtmlFolder();
            
            Path jsFolder = InputManager.getJsOutputFolder(htmlRoot);

            Path cssFolder = InputManager.getCssOutputFolder(htmlRoot);

            String contextPath = InputManager.readLine("Enter Application Context Path (optional): ");

            List<Path> htmlFiles = HtmlScanner.scanForHtmlFiles(htmlRoot);
            if (htmlFiles.isEmpty()) {
                System.out.println("No HTML/JSP files found to process.");
                LoggerManager.info("No HTML/JSP files found. Exiting.");
                return;
            }

            LoggerManager.info("Found " + htmlFiles.size() + " file(s) to process.");

            for (Path file : htmlFiles) {
                HtmlProcessor processor = new HtmlProcessor(file, htmlRoot, jsFolder, cssFolder, contextPath);
                try {
                    processor.process();
                } catch (ExitRequestedException e) {
                    System.out.println("Execution paused/exited by user request.");
                    LoggerManager.info("Execution paused/exited by user: " + e.getMessage());
                    break;
                } catch (Exception e) {
                    LoggerManager.error("Error processing file: " + file.getFileName(), e);
                }
            }

            System.out.println("=========================================");
            System.out.println("       CSP Migration Process Finished    ");
            System.out.println("=========================================");
            LoggerManager.info("CSP Migration Process Finished.");
            
        } catch (Exception e) {
            LoggerManager.fatal("Fatal error in application startup", e);
        }
    }
}
