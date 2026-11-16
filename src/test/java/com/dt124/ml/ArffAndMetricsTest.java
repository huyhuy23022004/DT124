package com.dt124.ml;

import com.dt124.dataset.ArffBuilder;
import com.dt124.model.EmailFeatureRecord;
import org.junit.jupiter.api.Test;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArffAndMetricsTest {

    @Test
    void arffBuilderUsesFullSchemaAndClassIndex() {
        List<EmailFeatureRecord> records = new ArrayList<>();
        records.add(record(0, 0, 0));
        records.add(record(1, 5, 3));
        records.add(record(0, 1, 1));
        records.add(record(1, 4, 2));

        Instances data = ArffBuilder.buildFull(records);

        assertEquals(FeatureSchema.ALL.size() + 1, data.numAttributes());
        assertEquals(FeatureSchema.CLASS_ATTRIBUTE, data.classAttribute().name());
        assertEquals(records.size(), data.numInstances());
    }

    @Test
    void metricAggregateComputesMeanSdAndCi() {
        MetricAggregate aggregate = MetricAggregate.of(List.of(0.8d, 0.9d, 1.0d));

        assertEquals(0.9d, aggregate.getMean(), 0.0001d);
        assertEquals(0.1d, aggregate.getSd(), 0.0001d);
        assertTrue(aggregate.getCi95() > 0);
        assertEquals(3, aggregate.getN());
    }

    @Test
    void metricsExposeConfusionMatrixSpecificityAndMcc() throws Exception {
        List<EmailFeatureRecord> records = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            records.add(record(i % 2, i % 2 == 0 ? 0 : 6, i % 2 == 0 ? 0 : 3));
        }
        Instances data = ArffBuilder.buildFull(records);
        int positiveIndex = data.classAttribute().indexOfValue(FeatureSchema.POSITIVE_CLASS);

        weka.classifiers.Classifier classifier = new weka.classifiers.bayes.NaiveBayes();
        weka.classifiers.Evaluation evaluation = new weka.classifiers.Evaluation(data);
        evaluation.crossValidateModel(classifier, data, 5, new java.util.Random(42));
        Metrics metrics = Metrics.fromEvaluation(evaluation, positiveIndex);

        assertEquals(records.size(), metrics.getTruePositives() + metrics.getTrueNegatives()
                + metrics.getFalsePositives() + metrics.getFalseNegatives(), 0.0001d);
        assertEquals(20.0d, metrics.getTrueNegatives() + metrics.getFalsePositives(), 0.0001d);
        assertEquals(20.0d, metrics.getTruePositives() + metrics.getFalseNegatives(), 0.0001d);
        assertTrue(metrics.getSpecificity() >= 0.0d && metrics.getSpecificity() <= 1.0d);
        assertTrue(metrics.getMcc() >= -1.0d && metrics.getMcc() <= 1.0d);
    }

    @Test
    void hybridClassifierProducesValidDistribution() throws Exception {
        List<EmailFeatureRecord> records = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            records.add(record(i % 2, i % 2 == 0 ? 0 : 6, i % 2 == 0 ? 0 : 3));
        }
        Instances data = ArffBuilder.buildFull(records);

        HybridScoringClassifier classifier = new HybridScoringClassifier();
        classifier.setSeed(42);
        classifier.buildClassifier(data);

        double[] distribution = classifier.distributionForInstance(data.instance(0));
        double sum = 0.0d;
        for (double value : distribution) {
            assertTrue(value >= 0.0d && value <= 1.0d);
            sum += value;
        }
        assertEquals(1.0d, sum, 0.0001d);
    }

    private EmailFeatureRecord record(int label, double keywordCount, double urlCount) {
        EmailFeatureRecord record = new EmailFeatureRecord();
        record.setLabel(label);
        record.put("kw_phishing_count", keywordCount);
        record.put("url_count", urlCount);
        record.put("spf_fail", label);
        record.put("from_return_path_mismatch", label);
        record.put("att_magic_mismatch", label);
        return record;
    }
}
