package com.dt124.ml;

import com.dt124.model.EmailFeatureRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Ablation study: measures how the proposed model degrades when each feature group is removed in
 * turn. Provides the "Full vs w/o Header-Auth vs w/o Paper-URL vs w/o Attachment-YARA" table.
 */
public final class AblationStudyRunner {

    private final ModelExperimentRunner runner;

    public AblationStudyRunner(ModelExperimentRunner runner) {
        this.runner = runner;
    }

    public List<ExperimentResult> run(List<EmailFeatureRecord> records) {
        List<ModelDefinition> definitions = new ArrayList<>();
        definitions.add(config("Ablation: Full", FeatureSchema.ALL));
        definitions.add(config("Ablation: w/o Header-Auth", FeatureSchema.allExcept(FeatureSchema.HEADER_AUTH)));
        definitions.add(config("Ablation: w/o Paper-URL", FeatureSchema.allExcept(FeatureSchema.PAPER_URL)));
        definitions.add(config("Ablation: w/o Attachment-YARA",
                FeatureSchema.allExcept(concat(FeatureSchema.ATTACHMENT, FeatureSchema.YARA))));
        return runner.runAll(definitions, records);
    }

    private ModelDefinition config(String name, List<String> features) {
        return new ModelDefinition(
                name,
                "Proposed hybrid model with features: " + features,
                features,
                seed -> {
                    HybridScoringClassifier classifier = new HybridScoringClassifier();
                    classifier.setSeed((int) seed);
                    return classifier;
                });
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> combined = new ArrayList<>(a);
        combined.addAll(b);
        return combined;
    }
}
