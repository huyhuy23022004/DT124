package com.dt124.dataset;

import com.dt124.extractor.EmailFeatureExtractor;
import com.dt124.extractor.ExtractionResult;
import com.dt124.extractor.SafeIndicatorRuleEngine;
import com.dt124.ml.FeatureSchema;
import com.dt124.ml.ModelDefinition;
import com.dt124.ml.ModelExperimentRunner;
import com.dt124.ml.ExperimentResult;
import com.dt124.ml.ClassifierFactory;
import com.dt124.model.EmailFeatureRecord;
import com.dt124.parser.OfflineEmlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhishingDetectionPipelineTest {

    @Test
    void endToEndPipelineDetectsPhishingBetterThanBaseline(@TempDir Path dir) throws Exception {
        Path benignDir = dir.resolve("benign");
        Path phishingDir = dir.resolve("phishing");
        new SyntheticEmailGenerator(7L).generate(benignDir, phishingDir, 60, 60);

        OfflineEmlParser parser = new OfflineEmlParser();
        SafeIndicatorRuleEngine rules = new SafeIndicatorRuleEngine();

        DatasetLoader benignLoader = new DatasetLoader(parser,
                new EmailFeatureExtractor(rules, "benign", EmailFeatureRecord.LABEL_BENIGN));
        DatasetLoader phishingLoader = new DatasetLoader(parser,
                new EmailFeatureExtractor(rules, "phishing", EmailFeatureRecord.LABEL_PHISHING));

        LoadStatistics stats = new LoadStatistics();
        List<ExtractionResult> results = new ArrayList<>();
        results.addAll(benignLoader.load(benignDir, "benign", EmailFeatureRecord.LABEL_BENIGN, true, 0, stats));
        results.addAll(phishingLoader.load(phishingDir, "phishing", EmailFeatureRecord.LABEL_PHISHING, true, 0, stats));

        assertTrue(stats.getTotalFiles() > 100, "samples plus injected duplicates");
        assertEquals(100.0d, stats.getCoveragePercent(), 0.001d, "all generated .eml must parse");

        int removed = new Deduplicator().deduplicate(results);
        assertTrue(removed >= 5, "the injected duplicate files must be detected");

        List<EmailFeatureRecord> records = new ArrayList<>();
        for (ExtractionResult result : results) {
            records.add(result.getFeatureRecord());
            // Every schema feature must be present after extraction.
            for (String feature : FeatureSchema.ALL) {
                assertTrue(result.getFeatureRecord().getFeatures().containsKey(feature),
                        "missing feature: " + feature);
            }
        }

        long benign = records.stream().filter(r -> r.getLabel() == 0).count();
        long phishing = records.stream().filter(r -> r.getLabel() == 1).count();
        assertEquals(benign, phishing, "balanced classes expected after dedup");

        ModelExperimentRunner runner = new ModelExperimentRunner(3, new long[]{42L}, 0.3d);
        ModelDefinition baselineDef = ClassifierFactory.baseline();
        ModelDefinition proposedDef = ClassifierFactory.proposedFull();

        ExperimentResult baseline = runner.run(baselineDef, records);
        ExperimentResult proposed = runner.run(proposedDef, records);

        assertTrue(proposed.getMeanF1() > baseline.getMeanF1(),
                "Proposed model F1 (" + proposed.getMeanF1() + ") must beat baseline ("
                        + baseline.getMeanF1() + ")");
        assertFalse(proposed.getMeanF1() < 0.5d, "proposed model should be clearly better than chance");
    }
}
