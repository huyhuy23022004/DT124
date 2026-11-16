package com.dt124.dataset;

import com.dt124.extractor.ExtractionResult;
import com.dt124.model.EmailFeatureRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeduplicatorTest {

    @Test
    void removesContentAndMessageIdDuplicates() {
        List<ExtractionResult> results = new ArrayList<>();
        results.add(record("id-1", "content-A"));
        results.add(record("id-2", "content-A")); // duplicate content
        results.add(record("id-1", "content-B")); // duplicate id
        results.add(record("id-3", "content-C")); // unique

        int removed = new Deduplicator().deduplicate(results);

        assertEquals(2, removed);
        assertEquals(2, results.size());
    }

    private ExtractionResult record(String id, String contentKey) {
        EmailFeatureRecord record = new EmailFeatureRecord();
        record.setId(id);
        record.setContentKey(contentKey);
        return new ExtractionResult(null, record, null, null, null);
    }
}
