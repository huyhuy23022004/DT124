package com.dt124.dataset;

import com.dt124.extractor.ExtractionResult;
import com.dt124.model.EmailFeatureRecord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes duplicate messages using two independent keys, mirroring the paper's dedup discipline:
 * a SHA-256 content key (normalized subject + body) and the Message-ID. This prevents the same email
 * appearing in multiple folders (inbox / sent / all_documents) from biasing the experiments.
 */
public final class Deduplicator {

    public int deduplicate(List<ExtractionResult> results) {
        Set<String> seenContent = new HashSet<>();
        Set<String> seenIds = new HashSet<>();
        List<ExtractionResult> unique = new ArrayList<>();
        int removed = 0;
        for (ExtractionResult result : results) {
            EmailFeatureRecord record = result.getFeatureRecord();
            String contentKey = record.getContentKey();
            String id = record.getId();
            boolean duplicate = (contentKey != null && !contentKey.isEmpty() && !seenContent.add(contentKey))
                    || (id != null && !id.isEmpty() && !seenIds.add(id));
            if (duplicate) {
                removed++;
                continue;
            }
            unique.add(result);
        }
        results.clear();
        results.addAll(unique);
        return removed;
    }
}
