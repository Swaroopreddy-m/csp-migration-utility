package com.csp.migration.model;

import java.util.concurrent.atomic.AtomicInteger;

public class ConversionReport {
    private final AtomicInteger htmlFilesProcessed = new AtomicInteger(0);
    private final AtomicInteger jsFilesGenerated = new AtomicInteger(0);
    private final AtomicInteger cssFilesGenerated = new AtomicInteger(0);
    
    private final AtomicInteger newScriptsAdded = new AtomicInteger(0);
    private final AtomicInteger newStylesAdded = new AtomicInteger(0);
    
    private final AtomicInteger duplicateScriptsRemoved = new AtomicInteger(0);
    private final AtomicInteger duplicateStylesRemoved = new AtomicInteger(0);
    
    private final AtomicInteger generatedClasses = new AtomicInteger(0);
    
    private final AtomicInteger warnings = new AtomicInteger(0);
    private final AtomicInteger errors = new AtomicInteger(0);

    public int getHtmlFilesProcessed() {
        return htmlFilesProcessed.get();
    }

    public void incrementHtmlFilesProcessed() {
        htmlFilesProcessed.incrementAndGet();
    }

    public int getJsFilesGenerated() {
        return jsFilesGenerated.get();
    }

    public void incrementJsFilesGenerated() {
        jsFilesGenerated.incrementAndGet();
    }

    public int getCssFilesGenerated() {
        return cssFilesGenerated.get();
    }

    public void incrementCssFilesGenerated() {
        cssFilesGenerated.incrementAndGet();
    }

    public int getNewScriptsAdded() {
        return newScriptsAdded.get();
    }

    public void addNewScriptsAdded(int count) {
        newScriptsAdded.addAndGet(count);
    }

    public int getNewStylesAdded() {
        return newStylesAdded.get();
    }

    public void addNewStylesAdded(int count) {
        newStylesAdded.addAndGet(count);
    }

    public int getDuplicateScriptsRemoved() {
        return duplicateScriptsRemoved.get();
    }

    public void addDuplicateScriptsRemoved(int count) {
        duplicateScriptsRemoved.addAndGet(count);
    }

    public int getDuplicateStylesRemoved() {
        return duplicateStylesRemoved.get();
    }

    public void addDuplicateStylesRemoved(int count) {
        duplicateStylesRemoved.addAndGet(count);
    }

    public int getGeneratedClasses() {
        return generatedClasses.get();
    }

    public void incrementGeneratedClasses() {
        generatedClasses.incrementAndGet();
    }
    
    public void addGeneratedClasses(int count) {
        generatedClasses.addAndGet(count);
    }

    public int getWarnings() {
        return warnings.get();
    }

    public void incrementWarnings() {
        warnings.incrementAndGet();
    }

    public int getErrors() {
        return errors.get();
    }

    public void incrementErrors() {
        errors.incrementAndGet();
    }
}
