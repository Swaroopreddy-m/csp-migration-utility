package com.csp.migration.service;

import com.csp.migration.model.ConversionState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CheckpointManager {
    private static final Path CHECKPOINT_FILE = Paths.get("conversion_state.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static synchronized void saveCheckpoint(ConversionState state) {
        try {
            String json = GSON.toJson(state);
            FileService.writeStringTransactionally(CHECKPOINT_FILE, json);
            LoggerService.info("Checkpoint saved at " + CHECKPOINT_FILE.toAbsolutePath());
        } catch (IOException e) {
            LoggerService.error("Failed to save checkpoint", e);
        }
    }

    public static synchronized ConversionState loadCheckpoint() {
        if (!Files.exists(CHECKPOINT_FILE)) {
            return null;
        }
        try {
            String json = Files.readString(CHECKPOINT_FILE);
            return GSON.fromJson(json, ConversionState.class);
        } catch (IOException e) {
            LoggerService.error("Failed to load checkpoint from " + CHECKPOINT_FILE.toAbsolutePath(), e);
            return null;
        }
    }

    public static synchronized void clearCheckpoint() {
        try {
            if (Files.deleteIfExists(CHECKPOINT_FILE)) {
                LoggerService.info("Cleared checkpoint file " + CHECKPOINT_FILE.toAbsolutePath());
            }
        } catch (IOException e) {
            LoggerService.error("Failed to delete checkpoint file " + CHECKPOINT_FILE.toAbsolutePath(), e);
        }
    }

    public static boolean checkpointExists() {
        return Files.exists(CHECKPOINT_FILE);
    }
    
    public static Path getCheckpointFilePath() {
        return CHECKPOINT_FILE;
    }
}
