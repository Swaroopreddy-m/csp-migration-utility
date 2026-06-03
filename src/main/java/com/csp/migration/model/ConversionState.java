package com.csp.migration.model;

import java.util.ArrayList;
import java.util.List;

public class ConversionState {
    private String mode; // "NEW_CONVERSION" or "UPDATE_CONVERSION"
    private String htmlFolder;
    private String jsFolder;
    private String cssFolder;
    private String phase;
    private List<String> processedFiles = new ArrayList<>();
    private List<String> generatedJsFiles = new ArrayList<>();
    private List<String> generatedCssFiles = new ArrayList<>();
    private int warningsCount;
    private int errorsCount;

    public ConversionState() {}

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getHtmlFolder() {
        return htmlFolder;
    }

    public void setHtmlFolder(String htmlFolder) {
        this.htmlFolder = htmlFolder;
    }

    public String getJsFolder() {
        return jsFolder;
    }

    public void setJsFolder(String jsFolder) {
        this.jsFolder = jsFolder;
    }

    public String getCssFolder() {
        return cssFolder;
    }

    public void setCssFolder(String cssFolder) {
        this.cssFolder = cssFolder;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public List<String> getProcessedFiles() {
        return processedFiles;
    }

    public void setProcessedFiles(List<String> processedFiles) {
        this.processedFiles = processedFiles;
    }

    public List<String> getGeneratedJsFiles() {
        return generatedJsFiles;
    }

    public void setGeneratedJsFiles(List<String> generatedJsFiles) {
        this.generatedJsFiles = generatedJsFiles;
    }

    public List<String> getGeneratedCssFiles() {
        return generatedCssFiles;
    }

    public void setGeneratedCssFiles(List<String> generatedCssFiles) {
        this.generatedCssFiles = generatedCssFiles;
    }

    public int getWarningsCount() {
        return warningsCount;
    }

    public void setWarningsCount(int warningsCount) {
        this.warningsCount = warningsCount;
    }

    public int getErrorsCount() {
        return errorsCount;
    }

    public void setErrorsCount(int errorsCount) {
        this.errorsCount = errorsCount;
    }
}
