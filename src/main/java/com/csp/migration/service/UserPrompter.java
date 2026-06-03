package com.csp.migration.service;

public interface UserPrompter {
    enum Choice {
        CONTINUE,
        SKIP_FILE,
        EXIT
    }

    Choice promptMissingScript(String fileName, String fullPath);
    Choice promptMissingStyle(String fileName, String fullPath);
}
