package com.dt124.ml;

import weka.classifiers.Classifier;

import java.util.List;

/**
 * Describes an experiment model: its display name, the feature subset it consumes and a factory that
 * creates a fresh, seeded Weka classifier for each run.
 */
public final class ModelDefinition {

    @FunctionalInterface
    public interface ClassifierSupplier {
        Classifier create(long seed);
    }

    private final String name;
    private final String description;
    private final List<String> features;
    private final ClassifierSupplier supplier;

    public ModelDefinition(String name, String description, List<String> features, ClassifierSupplier supplier) {
        this.name = name;
        this.description = description;
        this.features = features;
        this.supplier = supplier;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getFeatures() {
        return features;
    }

    public Classifier createClassifier(long seed) {
        return supplier.create(seed);
    }
}
