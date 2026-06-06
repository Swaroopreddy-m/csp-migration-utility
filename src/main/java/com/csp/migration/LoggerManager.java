package com.csp.migration;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LoggerManager {
    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    }

    private static Level currentLevel = Level.INFO;
    private static PrintWriter logWriter;

    static {
        try {
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            logWriter = new PrintWriter(new FileWriter("logs/csp-migration.log", true), true);
        } catch (IOException e) {
            System.err.println("Failed to initialize log file: " + e.getMessage());
        }
    }

    public static void setLevel(Level level) {
        currentLevel = level;
    }

    public static Level getLevel() {
        return currentLevel;
    }

    public static void trace(String message) {
        log(Level.TRACE, message, null);
    }

    public static void debug(String message) {
        log(Level.DEBUG, message, null);
    }

    public static void info(String message) {
        log(Level.INFO, message, null);
    }

    public static void warn(String message) {
        log(Level.WARN, message, null);
    }

    public static void error(String message) {
        log(Level.ERROR, message, null);
    }

    public static void error(String message, Throwable t) {
        log(Level.ERROR, message, t);
    }

    public static void fatal(String message) {
        log(Level.FATAL, message, null);
    }

    public static void fatal(String message, Throwable t) {
        log(Level.FATAL, message, t);
    }

    private static synchronized void log(Level level, String message, Throwable t) {
        if (level.ordinal() < currentLevel.ordinal()) {
            return;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StackTraceElement caller = null;
        boolean foundLogger = false;
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.equals(LoggerManager.class.getName())) {
                foundLogger = true;
            } else if (foundLogger) {
                caller = element;
                break;
            }
        }

        String callerClass = "Unknown";
        String callerMethod = "unknown";
        int lineNumber = -1;
        if (caller != null) {
            String fullClassName = caller.getClassName();
            callerClass = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
            callerMethod = caller.getMethodName();
            lineNumber = caller.getLineNumber();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(timestamp).append("\n");
        sb.append(level.name()).append("\n");
        sb.append(callerClass).append("\n");
        sb.append(callerMethod).append("\n");
        sb.append("Line ").append(lineNumber).append("\n\n");
        sb.append(message);
        if (t != null) {
            sb.append("\n");
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append(sw.toString());
        }

        String logEntry = sb.toString();
        
        if (level == Level.WARN || level == Level.ERROR || level == Level.FATAL) {
            System.err.println(logEntry);
            System.err.println();
        } else {
            System.out.println(logEntry);
            System.out.println();
        }

        if (logWriter != null) {
            logWriter.println(logEntry);
            logWriter.println();
        }
    }
}
