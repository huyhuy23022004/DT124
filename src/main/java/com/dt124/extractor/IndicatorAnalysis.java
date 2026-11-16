package com.dt124.extractor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregated YARA-style indicator hits for a single message. Rule names map to hit counts.
 */
public final class IndicatorAnalysis {

    private final Map<String, Integer> hits = new LinkedHashMap<>();

    public void put(String rule, int count) {
        hits.put(rule, count);
    }

    public Map<String, Integer> getHits() {
        return hits;
    }

    public int get(String rule) {
        Integer value = hits.get(rule);
        return value == null ? 0 : value;
    }

    public int getTotalHits() {
        int total = 0;
        for (int value : hits.values()) {
            total += value;
        }
        return total;
    }
}
