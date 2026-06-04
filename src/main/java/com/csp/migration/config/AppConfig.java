package com.csp.migration.config;

import java.nio.file.Path;

public class AppConfig {
    private Path htmlFolder;
    private Path jsFolder;
    private Path cssFolder;
    private String mode; // "NEW_CONVERSION" or "UPDATE_CONVERSION"
    private String applicationContextPath;

    public AppConfig() {}

    public AppConfig(Path htmlFolder, Path jsFolder, Path cssFolder, String mode) {
        this(htmlFolder, jsFolder, cssFolder, mode, null);
    }

    public AppConfig(Path htmlFolder, Path jsFolder, Path cssFolder, String mode, String applicationContextPath) {
        this.htmlFolder = htmlFolder;
        this.jsFolder = jsFolder;
        this.cssFolder = cssFolder;
        this.mode = mode;
        this.applicationContextPath = applicationContextPath;
    }

    public Path getHtmlFolder() {
        return htmlFolder;
    }

    public void setHtmlFolder(Path htmlFolder) {
        this.htmlFolder = htmlFolder;
    }

    public Path getJsFolder() {
        return jsFolder;
    }

    public void setJsFolder(Path jsFolder) {
        this.jsFolder = jsFolder;
    }

    public Path getCssFolder() {
        return cssFolder;
    }

    public void setCssFolder(Path cssFolder) {
        this.cssFolder = cssFolder;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getApplicationContextPath() {
        return applicationContextPath;
    }

    public void setApplicationContextPath(String applicationContextPath) {
        this.applicationContextPath = applicationContextPath;
    }
}
