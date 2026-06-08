package com.csp.migration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InputManager {
    private static final Scanner SCANNER = new Scanner(System.in);
    private static final Object CONSOLE_LOCK = new Object();

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

    public static int promptMenu(String title, String[] options) {
        synchronized (CONSOLE_LOCK) {
            while (true) {
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

    public static Path getAndValidateHtmlFolder() {
        while (true) {
            String input = readLine("Enter HTML files location: ");
            if (input.isEmpty()) {
                System.out.println("Error: HTML location is mandatory.");
                continue;
            }
            Path path = Paths.get(input).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                System.out.println("Error: Path does not exist: " + path);
                continue;
            }
            if (!Files.isReadable(path)) {
                System.out.println("Error: Path is not readable: " + path);
                continue;
            }
            if (!Files.isDirectory(path)) {
                String lower = path.toString().toLowerCase();
                if (lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".jsp")) {
                    return path;
                }
                System.out.println("Error: Path must be a directory or a supported file (.html, .htm, .jsp).");
                continue;
            }
            try {
                boolean hasHtml = false;
                try (Stream<Path> stream = Files.walk(path)) {
                    hasHtml = stream.anyMatch(p -> Files.isRegularFile(p) && 
                        (p.toString().toLowerCase().endsWith(".html") || 
                         p.toString().toLowerCase().endsWith(".htm") || 
                         p.toString().toLowerCase().endsWith(".jsp")));
                }
                if (!hasHtml) {
                    System.out.println("Error: Directory contains no HTML/JSP files: " + path);
                    continue;
                }
                return path;
            } catch (IOException e) {
                System.out.println("Error scanning directory: " + e.getMessage());
            }
        }
    }

    public static Path getJsOutputFolder(Path htmlRoot) {
        while (true) {
            String input = readLine("Enter JavaScript folder location: ");
            if (input.isEmpty()) {
                System.out.println("Error: JavaScript folder location is mandatory.");
                continue;
            }
            Path path = Paths.get(input);
            if (!path.isAbsolute()) {
                path = htmlRoot.resolve(path).toAbsolutePath().normalize();
            } else {
                path = path.toAbsolutePath().normalize();
            }
            
            try {
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                }
                return path;
            } catch (IOException e) {
                System.out.println("Error: Could not create folder: " + e.getMessage());
            }
        }
    }

    public static Path getCssOutputFolder(Path htmlRoot) {
        while (true) {
            String input = readLine("Enter CSS folder location: ");
            if (input.isEmpty()) {
                System.out.println("Error: CSS folder location is mandatory.");
                continue;
            }
            Path path = Paths.get(input);
            if (!path.isAbsolute()) {
                path = htmlRoot.resolve(path).toAbsolutePath().normalize();
            } else {
                path = path.toAbsolutePath().normalize();
            }
            
            try {
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                }
                return path;
            } catch (IOException e) {
                System.out.println("Error: Could not create folder: " + e.getMessage());
            }
        }
    }

    public static boolean confirmStage(String stageName, String promptMessage) throws com.csp.migration.exception.ExitRequestedException {
        System.out.println();
        System.out.println(stageName);
        System.out.println(promptMessage);
        String[] options = {"Continue", "Exit"};
        int choice = promptMenu(null, options);
        if (choice == 2) {
            throw new com.csp.migration.exception.ExitRequestedException("User chose to exit during " + stageName);
        }
        return true;
    }

    public static int promptMissingScript(String pathStr) {
        synchronized (CONSOLE_LOCK) {
            System.out.println();
            System.out.println("Script file not found:\n" + pathStr);
            System.out.println("\nProvide location?");
            String[] options = {
                "Enter path",
                "Ignore"
            };
            return promptMenu(null, options);
        }
    }

    public static int promptMissingStylesheet(String pathStr) {
        synchronized (CONSOLE_LOCK) {
            System.out.println();
            System.out.println("Stylesheet not found:\n" + pathStr);
            System.out.println();
            String[] options = {
                "Enter location",
                "Ignore"
            };
            return promptMenu(null, options);
        }
    }
}
