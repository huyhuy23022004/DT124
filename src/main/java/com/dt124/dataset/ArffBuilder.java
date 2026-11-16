package com.dt124.dataset;

import com.dt124.ml.FeatureSchema;
import com.dt124.model.EmailFeatureRecord;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds Weka {@link Instances} from {@link EmailFeatureRecord}s. The attribute order always follows
 * the requested feature list, guaranteeing that the parser, experiments and ablation study use an
 * identical layout.
 */
public final class ArffBuilder {

    private ArffBuilder() {
    }

    public static Instances build(List<EmailFeatureRecord> records, List<String> features) {
        return build(records, features, "dt124");
    }

    public static Instances build(List<EmailFeatureRecord> records, List<String> features, String relationName) {
        ArrayList<Attribute> attributes = new ArrayList<>();
        for (String feature : features) {
            attributes.add(new Attribute(feature));
        }
        Attribute classAttribute = new Attribute(FeatureSchema.CLASS_ATTRIBUTE,
                List.of(FeatureSchema.NEGATIVE_CLASS, FeatureSchema.POSITIVE_CLASS));
        attributes.add(classAttribute);

        Instances instances = new Instances(relationName, attributes, records.size());
        instances.setClassIndex(attributes.size() - 1);

        for (EmailFeatureRecord record : records) {
            double[] values = new double[attributes.size()];
            for (int i = 0; i < features.size(); i++) {
                values[i] = record.get(features.get(i));
            }
            values[features.size()] = record.getLabel();
            Instance instance = new DenseInstance(1.0d, values);
            instance.setDataset(instances);
            instances.add(instance);
        }
        return instances;
    }

    /**
     * Builds instances containing every schema feature.
     */
    public static Instances buildFull(List<EmailFeatureRecord> records) {
        return build(records, FeatureSchema.ALL, "dt124_full");
    }
}
