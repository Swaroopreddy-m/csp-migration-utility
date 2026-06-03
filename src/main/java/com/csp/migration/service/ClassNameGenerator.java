package com.csp.migration.service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassNameGenerator {
    private final AtomicInteger counter = new AtomicInteger(0);
    private static final Pattern CSP_AUTO_PATTERN = Pattern.compile("csp_auto_(\\d{4})");

    public ClassNameGenerator() {}

    /**
     * Scans content (HTML, JS, CSS, etc.) for any existing csp_auto_XXXX classes
     * and sets the counter to max found.
     */
    public void scanExistingClasses(String content) {
        if (content == null) return;
        Matcher matcher = CSP_AUTO_PATTERN.matcher(content);
        int max = 0;
        while (matcher.find()) {
            try {
                int val = Integer.parseInt(matcher.group(1));
                if (val > max) {
                    max = val;
                }
            } catch (NumberFormatException ignored) {}
        }
        
        // Atomically set counter to max if max is greater than current
        while (true) {
            int current = counter.get();
            if (max <= current) {
                break;
            }
            if (counter.compareAndSet(current, max)) {
                break;
            }
        }
    }

    /**
     * Generates the next sequential class name.
     */
    public String generateNext() {
        int nextVal = counter.incrementAndGet();
        return String.format("csp_auto_%04d", nextVal);
    }

    public int getCurrentCount() {
        return counter.get();
    }
}
