package com.dt124.ml;

import com.dt124.dataset.ArffBuilder;
import com.dt124.dataset.GroupBasedSplitter;
import com.dt124.model.EmailFeatureRecord;
import com.dt124.util.LogSanitizer;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces representative False Positive / False Negative examples for the error-analysis section.
 * Uses a group-disjoint holdout so the reported errors are leakage-free.
 */
public final class ErrorAnalyzer {

    private static final int MAX_EXAMPLES = 8;

    public List<String> analyze(ModelDefinition model, List<EmailFeatureRecord> records,
                                double testFraction, long seed) {
        List<String> lines = new ArrayList<>();
        try {
            GroupBasedSplitter.Split split = new GroupBasedSplitter().split(records, testFraction, seed);
            List<EmailFeatureRecord> trainRecords = select(records, split.getTrainIndices());
            List<EmailFeatureRecord> testRecords = select(records, split.getTestIndices());
            if (trainRecords.isEmpty() || testRecords.isEmpty()) {
                return lines;
            }
            Instances train = ArffBuilder.build(trainRecords, model.getFeatures(), "err_train");
            Instances test = ArffBuilder.build(testRecords, model.getFeatures(), "err_test");

            Classifier classifier = model.createClassifier(seed);
            classifier.buildClassifier(train);
            Evaluation evaluation = new Evaluation(train);
            evaluation.evaluateModel(classifier, test);

            int fp = 0;
            int fn = 0;
            for (int i = 0; i < test.numInstances() && (fp < MAX_EXAMPLES || fn < MAX_EXAMPLES); i++) {
                var prediction = evaluation.predictions().get(i);
                double actual = prediction.actual();
                double predicted = prediction.predicted();
                if (actual == predicted) {
                    continue;
                }
                EmailFeatureRecord record = testRecords.get(i);
                String subject = record.getAuthResult() == null ? "" : record.getAuthResult().getSubject();
                if (actual == EmailFeatureRecord.LABEL_BENIGN) {
                    if (fp >= MAX_EXAMPLES) {
                        continue;
                    }
                    fp++;
                } else {
                    if (fn >= MAX_EXAMPLES) {
                        continue;
                    }
                    fn++;
                }
                String label = actual == EmailFeatureRecord.LABEL_BENIGN
                        ? "FP (benign->phishing)" : "FN (phishing->missed)";
                lines.add(String.format(java.util.Locale.ROOT,
                        "%-22s src=%s subject=\"%s\" url_count=%.0f spf_fail=%.0f att_magic=%.0f",
                        label,
                        LogSanitizer.sanitize(shortPath(record.getSourcePath())),
                        LogSanitizer.sanitize(truncate(subject)),
                        record.get("url_count"),
                        record.get("spf_fail"),
                        record.get("att_magic_mismatch")));
            }
        } catch (Exception e) {
            lines.add("Error analysis failed: " + e.getMessage());
        }
        return lines;
    }

    private static String shortPath(String path) {
        if (path == null) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 60 ? value.substring(0, 57) + "..." : value;
    }

    private static List<EmailFeatureRecord> select(List<EmailFeatureRecord> records, List<Integer> indices) {
        List<EmailFeatureRecord> selected = new ArrayList<>();
        for (int index : indices) {
            selected.add(records.get(index));
        }
        return selected;
    }
}
