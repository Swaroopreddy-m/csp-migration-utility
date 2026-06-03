package com.csp.migration.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LoggerService {
    private static final Path LOG_DIR = Paths.get("logs");
    private static final Path APP_LOG = LOG_DIR.resolve("app.log");
    private static final Path WARNING_LOG = LOG_DIR.resolve("warning.log");
    private static final Path ERROR_LOG = LOG_DIR.resolve("error.log");
    private static final Path UPDATE_LOG = LOG_DIR.resolve("update.log");

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    static {
        try {
            Files.createDirectories(LOG_DIR);
        } catch (IOException e) {
            System.err.println("Failed to create log directory: " + e.getMessage());
        }
    }

    private static synchronized void log(Path logFile, String level, String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String logEntry = String.format("[%s] [%s] - %s%n", timestamp, level, message);
        try {
            Files.writeString(logFile, logEntry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write to log file " + logFile.getFileName() + ": " + e.getMessage());
        }
    }

    public static void clearLogs() {
        try {
            Files.deleteIfExists(APP_LOG);
            Files.deleteIfExists(WARNING_LOG);
            Files.deleteIfExists(ERROR_LOG);
            Files.deleteIfExists(UPDATE_LOG);
        } catch (IOException e) {
            System.err.println("Failed to clear log files: " + e.getMessage());
        }
    }

    public static void info(String message) {
        log(APP_LOG, "INFO", message);
    }

    public static void warning(String message) {
        log(APP_LOG, "WARN", message);
        log(WARNING_LOG, "WARN", message);
    }

    public static void error(String message) {
        log(APP_LOG, "ERROR", message);
        log(ERROR_LOG, "ERROR", message);
    }

    public static void error(String message, Throwable t) {
        StringBuilder sb = new StringBuilder(message).append(System.lineSeparator());
        if (t != null) {
            sb.append(t.toString()).append(System.lineSeparator());
            for (StackTraceElement element : t.getStackTrace()) {
                sb.append("\tat ").append(element.toString()).append(System.lineSeparator());
            }
        }
        error(sb.toString().trim());
    }

    public static void update(String message) {
        log(APP_LOG, "UPDATE", message);
        log(UPDATE_LOG, "UPDATE", message);
    }
}
