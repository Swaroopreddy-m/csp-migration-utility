package com.csp.migration.model;

import java.util.List;
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

    // Detailed collections for the final migration report
    private final List<String> htmlFilesList = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<String> cssFilesList = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<String> jsFilesList = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<String> missingResources = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.Map<String, String> userResourceMappings = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<String> inlineStylesExtracted = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<String> existingIdsReused = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<String> idsGenerated = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<String> displayNoneConversions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<String> jsVisibilityConversions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<String> manualReviewWarnings = new java.util.concurrent.CopyOnWriteArrayList<>();

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

    // Detail Getters & Mutators
    public List<String> getHtmlFilesList() { return htmlFilesList; }
    public void addHtmlFile(String file) { 
        if (!htmlFilesList.contains(file)) {
            htmlFilesList.add(file);
        }
    }

    public List<String> getCssFilesList() { return cssFilesList; }
    public void addCssFile(String file) {
        if (!cssFilesList.contains(file)) {
            cssFilesList.add(file);
        }
    }

    public List<String> getJsFilesList() { return jsFilesList; }
    public void addJsFile(String file) {
        if (!jsFilesList.contains(file)) {
            jsFilesList.add(file);
        }
    }

    public List<String> getMissingResources() { return missingResources; }
    public void addMissingResource(String res) {
        if (!missingResources.contains(res)) {
            missingResources.add(res);
        }
    }

    public java.util.Map<String, String> getUserResourceMappings() { return userResourceMappings; }
    public void addUserResourceMapping(String original, String resolved) {
        userResourceMappings.put(original, resolved);
    }

    public List<String> getInlineStylesExtracted() { return inlineStylesExtracted; }
    public void addInlineStyleExtracted(String style) {
        inlineStylesExtracted.add(style);
    }

    public List<String> getExistingIdsReused() { return existingIdsReused; }
    public void addExistingIdReused(String id) {
        existingIdsReused.add(id);
    }

    public List<String> getIdsGenerated() { return idsGenerated; }
    public void addIdGenerated(String id) {
        idsGenerated.add(id);
    }

    public List<String> getDisplayNoneConversions() { return displayNoneConversions; }
    public void addDisplayNoneConversion(String desc) {
        displayNoneConversions.add(desc);
    }

    public List<String> getJsVisibilityConversions() { return jsVisibilityConversions; }
    public void addJsVisibilityConversion(String desc) {
        jsVisibilityConversions.add(desc);
    }

    public List<String> getManualReviewWarnings() { return manualReviewWarnings; }
    public void addManualReviewWarning(String warn) {
        manualReviewWarnings.add(warn);
        incrementWarnings();
    }
}
