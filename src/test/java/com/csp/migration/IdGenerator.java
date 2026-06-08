package com.csp.migration;

import java.util.UUID;
import java.nio.charset.StandardCharsets;

public class IdGenerator {
    private int counter = 0;
    private final String seed;

    public IdGenerator(String seed) {
        this.seed = seed;
    }

    public synchronized String generateNextId() {
        counter++;
        String source = seed + "_" + counter;
        UUID uuid = UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
        return "csp_auto_" + uuid.toString();
    }
}
