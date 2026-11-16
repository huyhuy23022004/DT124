package com.dt124.ml;

import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.trees.RandomForest;

import java.util.List;

/**
 * Factory for the three models compared in the experiments:
 * <ol>
 *   <li><b>Baseline</b> - Naive Bayes over the minimal keyword/URL feature set.</li>
 *   <li><b>Paper URL-Only Variant</b> - Random Forest over the tyag020 URL/domain features only.</li>
 *   <li><b>Proposed Full Model</b> - the weighted hybrid Random Forest + rule score over all
 *       four feature groups.</li>
 * </ol>
 */
public final class ClassifierFactory {

    private ClassifierFactory() {
    }

    public static ModelDefinition baseline() {
        return new ModelDefinition(
                "Baseline (NaiveBayes)",
                "Naive Bayes on keyword + url_count + subject_len only",
                FeatureSchema.BASELINE,
                seed -> new NaiveBayes());
    }

    public static ModelDefinition paperUrlOnly() {
        return new ModelDefinition(
                "Paper URL-Only (RandomForest)",
                "Random Forest over tyag020 URL/domain features only",
                FeatureSchema.PAPER_URL,
                seed -> {
                    RandomForest forest = new RandomForest();
                    forest.setNumIterations(150);
                    forest.setSeed((int) seed);
                    return forest;
                });
    }

    public static ModelDefinition proposedFull() {
        return new ModelDefinition(
                "Proposed Hybrid Full (RF + Rule)",
                "Weighted hybrid RandomForest + rule score over all four feature groups",
                FeatureSchema.ALL,
                seed -> {
                    HybridScoringClassifier classifier = new HybridScoringClassifier();
                    classifier.setSeed((int) seed);
                    return classifier;
                });
    }

    public static List<ModelDefinition> allThree() {
        return List.of(baseline(), paperUrlOnly(), proposedFull());
    }
}
