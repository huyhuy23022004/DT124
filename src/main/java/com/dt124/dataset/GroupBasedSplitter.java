package com.dt124.dataset;

import com.dt124.model.EmailFeatureRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Group-aware splitter. Messages are grouped by sender domain (or another grouping key) so that all
 * messages from the same sender/thread stay on one side of the split. This prevents train/test
 * leakage that would otherwise inflate results.
 */
public final class GroupBasedSplitter {

    public static final class Split {
        private final List<Integer> trainIndices = new ArrayList<>();
        private final List<Integer> testIndices = new ArrayList<>();

        public List<Integer> getTrainIndices() {
            return trainIndices;
        }

        public List<Integer> getTestIndices() {
            return testIndices;
        }
    }

    public Split split(List<EmailFeatureRecord> records, double testFraction, long seed) {
        Split split = new Split();
        if (records.isEmpty()) {
            return split;
        }
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < records.size(); i++) {
            EmailFeatureRecord record = records.get(i);
            String key = record.getGroupKey();
            if (key == null || key.isEmpty()) {
                key = "__singleton_" + i;
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }

        List<String> groupKeys = new ArrayList<>(groups.keySet());
        java.util.Collections.shuffle(groupKeys, new Random(seed));

        int targetTest = (int) Math.round(records.size() * testFraction);
        int testCount = 0;
        for (String key : groupKeys) {
            List<Integer> indices = groups.get(key);
            if (testCount < targetTest) {
                split.getTestIndices().addAll(indices);
                testCount += indices.size();
            } else {
                split.getTrainIndices().addAll(indices);
            }
        }
        // Safety net: if the fraction produced an empty train or test set, fall back to a naive split.
        if (split.getTrainIndices().isEmpty() || split.getTestIndices().isEmpty()) {
            split.getTrainIndices().clear();
            split.getTestIndices().clear();
            for (int i = 0; i < records.size(); i++) {
                if (i % 5 == 0) {
                    split.getTestIndices().add(i);
                } else {
                    split.getTrainIndices().add(i);
                }
            }
        }
        return split;
    }
}
