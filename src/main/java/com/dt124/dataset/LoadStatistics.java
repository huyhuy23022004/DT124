package com.dt124.dataset;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parser coverage and dataset quality statistics collected while loading .eml corpora.
 */
public final class LoadStatistics {

    private int totalFiles;
    private int parsedOk;
    private int failed;
    private int duplicatesRemoved;
    private final Map<String, Integer> perCorpus = new LinkedHashMap<>();
    private final Map<String, Integer> perCorpusParsed = new LinkedHashMap<>();

    public void registerFile(String corpus) {
        totalFiles++;
        perCorpus.merge(corpus, 1, Integer::sum);
    }

    public void registerParsed(String corpus) {
        parsedOk++;
        perCorpusParsed.merge(corpus, 1, Integer::sum);
    }

    public void registerFailed() {
        failed++;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public int getParsedOk() {
        return parsedOk;
    }

    public int getFailed() {
        return failed;
    }

    public int getDuplicatesRemoved() {
        return duplicatesRemoved;
    }

    public void setDuplicatesRemoved(int duplicatesRemoved) {
        this.duplicatesRemoved = duplicatesRemoved;
    }

    public double getCoveragePercent() {
        if (totalFiles == 0) {
            return 0.0d;
        }
        return 100.0d * parsedOk / totalFiles;
    }

    public Map<String, Integer> getPerCorpus() {
        return perCorpus;
    }

    public Map<String, Integer> getPerCorpusParsed() {
        return perCorpusParsed;
    }
}
