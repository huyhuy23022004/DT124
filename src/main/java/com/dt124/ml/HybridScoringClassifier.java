
package com.dt124.ml;

import weka.classifiers.RandomizableClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * DT124 proposed model: a weighted hybrid of a Random Forest classifier (over the full feature set)
 * and an explicit expert rule score derived from high-value phishing indicators (authentication
 * mismatches, anchor mismatch, dangerous / magic-mismatched attachments, obfuscation indicators).
 *
 * <p>The rule score is combined with the forest's phishing probability, which sharpens decisions on
 * the rare, high-confidence signals that a tree ensemble alone can underweight on small datasets.
 * Implemented as a Weka classifier so that it can be evaluated with standard cross-validation.</p>
 */
public final class HybridScoringClassifier extends RandomizableClassifier {

    private static final long serialVersionUID = 1L;

    private static final double FOREST_WEIGHT = 0.65d;
    private static final double RULE_WEIGHT = 0.35d;
    private static final double RULE_SCALE = 6.0d;

    private RandomForest forest;
    private Instances header;
    private int phishingIndex;

    @Override
    public void buildClassifier(Instances data) throws Exception {
        Instances training = new Instances(data);
        training.deleteWithMissingClass();
        header = new Instances(training, 0);
        phishingIndex = training.classAttribute().indexOfValue(FeatureSchema.POSITIVE_CLASS);
        if (phishingIndex < 0) {
            phishingIndex = 1;
        }

        forest = new RandomForest();
        forest.setNumIterations(150);
        forest.setSeed(getSeed());
        forest.setNumFeatures(0);
        forest.setMaxDepth(0);
        forest.buildClassifier(training);
    }

    @Override
    public double[] distributionForInstance(Instance instance) throws Exception {
        double[] forestDistribution = forest.distributionForInstance(instance);
        int numClasses = instance.numClasses();
        double forestPhishing = phishingIndex < forestDistribution.length
                ? forestDistribution[phishingIndex] : forestDistribution[forestDistribution.length - 1];

        double ruleProbability = ruleScore(instance);
        double combined = FOREST_WEIGHT * forestPhishing + RULE_WEIGHT * ruleProbability;
        combined = Math.max(0.0d, Math.min(1.0d, combined));

        double[] output = new double[numClasses];
        output[phishingIndex] = combined;
        int benignIndex = phishingIndex == 0 ? 1 : 0;
        if (benignIndex < numClasses) {
            output[benignIndex] = 1.0d - combined;
        }
        return output;
    }

    private double ruleScore(Instance instance) {
        double score = 0.0d;
        score += 3.0d * value(instance, "att_magic_mismatch");
        score += 2.0d * value(instance, "att_double_ext");
        score += 2.0d * value(instance, "att_dangerous_ext");
        score += 2.0d * value(instance, "from_return_path_mismatch");
        score += 1.5d * value(instance, "spf_fail");
        score += 1.0d * value(instance, "dkim_fail");
        score += 1.0d * value(instance, "dmarc_fail");
        score += 1.5d * value(instance, "html_anchor_mismatch");
        score += 1.0d * value(instance, "url_free_hosting_abuse");
        score += 1.5d * value(instance, "url_ip_count");
        score += 1.0d * value(instance, "url_brand_hyphen");
        score += 1.0d * value(instance, "url_suspicious_tld");
        score += 2.0d * value(instance, "yara_eval_obfuscation");
        score += 1.5d * value(instance, "yara_hidden_login_form");
        score += 1.0d * value(instance, "yara_base64_blob");
        score += 0.5d * value(instance, "yara_total_hits");
        score += 0.3d * value(instance, "kw_phishing_count");
        score += 0.5d * value(instance, "spf_missing");
        score += 0.4d * value(instance, "domain_entropy_max");
        return 1.0d - Math.exp(-score / RULE_SCALE);
    }

    private double value(Instance instance, String attributeName) {
        if (header == null) {
            return 0.0d;
        }
        Attribute attribute = header.attribute(attributeName);
        if (attribute == null) {
            return 0.0d;
        }
        int index = attribute.index();
        if (index >= instance.numAttributes() || instance.isMissing(index)) {
            return 0.0d;
        }
        return instance.value(index);
    }

    @Override
    public String toString() {
        return HybridScoringClassifier.class.getSimpleName()
                + " (RandomForest + rule score, weights " + FOREST_WEIGHT + "/" + RULE_WEIGHT + ")";
    }
}
