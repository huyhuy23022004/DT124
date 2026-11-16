package com.dt124.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Experiment configuration, loaded from {@code configs/experiment_config.json} when present.
 * Sensible defaults are used otherwise so the demo always runs.
 */
public final class AppConfig {

    public List<Integer> seeds = new ArrayList<>(List.of(42, 100, 2026));
    public int folds = 5;
    public int benignCount = 250;
    public int phishingCount = 250;
    public long generatorSeed = 20260101L;
    public double groupHoldoutFraction = 0.3d;
    public int maxEnronFiles = 0;
    public String samplesDir = "data/samples";
    public String enronDir = "data/raw/enron";
    public String phishingDir = "data/raw/phishing_eml";
    public String artifactsDir = "artifacts";
    public String rulesPath = "rules/safe_indicators.yar";
    public String manifestPath = "data/manifests/checksums_sha256.txt";
    public boolean regenerateSamples = false;

    public static AppConfig load(Path path) {
        AppConfig config = new AppConfig();
        if (path != null && Files.isRegularFile(path)) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                config = mapper.readValue(path.toFile(), AppConfig.class);
            } catch (IOException e) {
                System.err.println("[config] Failed to read " + path + ", using defaults: " + e.getMessage());
                config = new AppConfig();
            }
        }
        if (config.seeds == null || config.seeds.isEmpty()) {
            config.seeds = new ArrayList<>(List.of(42, 100, 2026));
        }
        if (config.folds < 2) {
            config.folds = 5;
        }
        return config;
    }

    public long[] seedArray() {
        long[] array = new long[seeds.size()];
        for (int i = 0; i < seeds.size(); i++) {
            array[i] = seeds.get(i);
        }
        return array;
    }
}
