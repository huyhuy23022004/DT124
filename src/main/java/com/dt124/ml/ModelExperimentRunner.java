package com.dt124.ml;

import com.dt124.dataset.ArffBuilder;
import com.dt124.dataset.GroupBasedSplitter;
import com.dt124.model.EmailFeatureRecord;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Runs the controlled experiment matrix: {@code folds}-fold cross-validation repeated over a fixed
 * set of random seeds, plus a group-disjoint holdout evaluation to quantify leakage-free
 * generalisation. The positive class is always "phishing".
 */
public final class ModelExperimentRunner {

    private static final Logger LOG = Logger.getLogger(ModelExperimentRunner.class.getName());

    private final int folds;
    private final long[] seeds;
    private final double groupHoldoutFraction;

    public ModelExperimentRunner(int folds, long[] seeds, double groupHoldoutFraction) {
        this.folds = folds;
        this.seeds = seeds.clone();
        this.groupHoldoutFraction = groupHoldoutFraction;
    }

    public List<ExperimentResult> runAll(List<ModelDefinition> models, List<EmailFeatureRecord> records) {
        List<ExperimentResult> results = new ArrayList<>();
        for (ModelDefinition model : models) {
            results.add(run(model, records));
        }
        return results;
    }

    public ExperimentResult run(ModelDefinition model, List<EmailFeatureRecord> records) {
        Instances data = ArffBuilder.build(records, model.getFeatures(), "dt124_" + sanitize(model.getName()));
        int positiveIndex = data.classAttribute().indexOfValue(FeatureSchema.POSITIVE_CLASS);

        List<Long> seedList = new ArrayList<>();
        List<Metrics> perSeed = new ArrayList<>();

        for (long seed : seeds) {
            seedList.add(seed);
            try {
                Classifier classifier = model.createClassifier(seed);
                Evaluation evaluation = new Evaluation(data);
                evaluation.crossValidateModel(classifier, data, folds, new Random(seed));
                perSeed.add(Metrics.fromEvaluation(evaluation, positiveIndex));
            } catch (Exception e) {
                LOG.warning("Model " + model.getName() + " seed " + seed + " failed: " + e.getMessage());
                perSeed.add(Metrics.empty());
            }
        }

        Metrics groupHoldout = evaluateGroupHoldout(model, records, seeds.length > 0 ? seeds[0] : 42L);
        return new ExperimentResult(model.getName(), model.getDescription(),
                model.getFeatures().size(), seedList, perSeed, groupHoldout);
    }

    private Metrics evaluateGroupHoldout(ModelDefinition model, List<EmailFeatureRecord> records, long seed) {
        try {
            GroupBasedSplitter splitter = new GroupBasedSplitter();
            GroupBasedSplitter.Split split = splitter.split(records, groupHoldoutFraction, seed);
            if (split.getTrainIndices().isEmpty() || split.getTestIndices().isEmpty()) {
                return null;
            }
            List<EmailFeatureRecord> trainRecords = select(records, split.getTrainIndices());
            List<EmailFeatureRecord> testRecords = select(records, split.getTestIndices());

            Instances train = ArffBuilder.build(trainRecords, model.getFeatures(), "dt124_train");
            Instances test = ArffBuilder.build(testRecords, model.getFeatures(), "dt124_test");
            if (train.numInstances() < 2 || test.numInstances() < 1) {
                return null;
            }
            int positiveIndex = test.classAttribute().indexOfValue(FeatureSchema.POSITIVE_CLASS);

            Classifier classifier = model.createClassifier(seed);
            classifier.buildClassifier(train);
            Evaluation evaluation = new Evaluation(train);
            evaluation.evaluateModel(classifier, test);
            return Metrics.fromEvaluation(evaluation, positiveIndex);
        } catch (Exception e) {
            LOG.warning("Group holdout failed for " + model.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private static List<EmailFeatureRecord> select(List<EmailFeatureRecord> records, List<Integer> indices) {
        List<EmailFeatureRecord> selected = new ArrayList<>();
        for (int index : indices) {
            selected.add(records.get(index));
        }
        return selected;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9]+", "_");
    }
}
