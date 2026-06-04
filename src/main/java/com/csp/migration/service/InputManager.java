package com.csp.migration.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class InputManager {
    private static final Scanner SCANNER = new Scanner(System.in);
    private static final Object CONSOLE_LOCK = new Object();

    /**
     * Reads a line of input from console with a prompt. Synchronized to avoid multi-thread interleaving.
     */
    public static String readLine(String prompt) {
        synchronized (CONSOLE_LOCK) {
            System.out.print(prompt);
            System.out.flush();
            if (SCANNER.hasNextLine()) {
                return SCANNER.nextLine().trim();
            }
            return "";
        }
    }

    /**
     * Prompts the user to select an option from a list of options.
     */
    public static int promptMenu(String title, String[] options) {
        synchronized (CONSOLE_LOCK) {
            while (true) {
                System.out.println();
                if (title != null && !title.isEmpty()) {
                    System.out.println(title);
                }
                for (int i = 0; i < options.length; i++) {
                    System.out.println((i + 1) + ". " + options[i]);
                }
                System.out.print("Enter choice: ");
                System.out.flush();
                String input = SCANNER.hasNextLine() ? SCANNER.nextLine().trim() : "";
                try {
                    int choice = Integer.parseInt(input);
                    if (choice >= 1 && choice <= options.length) {
                        return choice;
                    }
                } catch (NumberFormatException ignored) {}
                System.out.println("Invalid selection. Please enter a number between 1 and " + options.length + ".");
            }
        }
    }

    /**
     * Validates and returns the HTML folder path. Throws if invalid or missing.
     */
    public static Path getAndValidateHtmlFolder() {
        String input = readLine("Enter HTML Folder Location: ");
        if (input.isEmpty()) {
            System.err.println("HTML folder is mandatory.");
            System.exit(1);
        }
        Path path = Paths.get(input).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            System.err.println("Error: HTML Folder does not exist or is not a directory at " + path);
            System.exit(1);
        }
        return path;
    }

    /**
     * Prompt the user upfront for the Application Context Path (optional).
     * Used for resolving DYN_CONTEXT_PATH.
     */
    public static String getAndValidateApplicationContextPath() {
        String input = readLine("Enter Application Context Path (optional, for resolving DYN_CONTEXT_PATH): ");
        if (input.isEmpty()) {
            return "";
        }
        Path path = Paths.get(input).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            System.out.println("Warning: Application Context Path does not exist or is not a directory at " + path);
            System.out.println("We will still use it for string replacement, but file resolution may fail.");
        }
        return path.toString();
    }

    /**
     * Validates and returns the HTML file or folder path for update mode.
     */
    public static Path getAndValidateHtmlLocationForUpdate() {
        String input = readLine("Enter HTML File/Folder Location: ");
        if (input.isEmpty()) {
            System.err.println("HTML location is mandatory.");
            System.exit(1);
        }
        Path path = Paths.get(input).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            System.err.println("Error: HTML path does not exist at " + path);
            System.exit(1);
        }
        return path;
    }

    /**
     * Prompts and auto-creates JS output folder.
     */
    public static Path getJsOutputFolder() {
        String input = readLine("Enter JavaScript Output Folder: ");
        if (input.isEmpty()) {
            input = "js"; // Default
        }
        Path path = Paths.get(input).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            System.err.println("Error: Could not create JS directory: " + e.getMessage());
            System.exit(1);
        }
        return path;
    }

    /**
     * Prompts and auto-creates CSS output folder.
     */
    public static Path getCssOutputFolder() {
        String input = readLine("Enter CSS Output Folder: ");
        if (input.isEmpty()) {
            input = "css"; // Default
        }
        Path path = Paths.get(input).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            System.err.println("Error: Could not create CSS directory: " + e.getMessage());
            System.exit(1);
        }
        return path;
    }

    /**
     * Thread-safe prompter implementation for missing files.
     */
    public static UserPrompter getCliPrompter() {
        return new UserPrompter() {
            @Override
            public Choice promptMissingScript(String fileName, String fullPath) {
                synchronized (CONSOLE_LOCK) {
                    System.out.println();
                    System.out.println("Script file not found: " + fullPath);
                    String[] options = {"Continue", "Skip Current File", "Exit"};
                    int choice = promptMenu(null, options);
                    if (choice == 1) return Choice.CONTINUE;
                    if (choice == 2) return Choice.SKIP_FILE;
                    return Choice.EXIT;
                }
            }

            @Override
            public Choice promptMissingStyle(String fileName, String fullPath) {
                synchronized (CONSOLE_LOCK) {
                    System.out.println();
                    System.out.println("Style file not found: " + fullPath);
                    String[] options = {"Continue", "Skip", "Exit"};
                    int choice = promptMenu(null, options);
                    if (choice == 1) return Choice.CONTINUE;
                    if (choice == 2) return Choice.SKIP_FILE;
                    return Choice.EXIT;
                }
            }
        };
    }
}
