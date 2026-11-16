package com.dt124.ml;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of running one model across multiple seeds: the per-seed metrics plus aggregated
 * mean/SD/95% CI for every reported metric.
 */
public final class ExperimentResult {

    private final String modelName;
    private final String description;
    private final int featureCount;
    private final List<Long> seeds;
    private final List<Metrics> perSeedMetrics;
    private final Metrics groupHoldoutMetrics;

    private final MetricAggregate precision;
    private final MetricAggregate recall;
    private final MetricAggregate f1;
    private final MetricAggregate macroF1;
    private final MetricAggregate rocAuc;
    private final MetricAggregate prAuc;
    private final MetricAggregate fpr;
    private final MetricAggregate accuracy;
    private final MetricAggregate specificity;
    private final MetricAggregate mcc;

    public ExperimentResult(String modelName, String description, int featureCount, List<Long> seeds,
                            List<Metrics> perSeedMetrics, Metrics groupHoldoutMetrics) {
        this.modelName = modelName;
        this.description = description;
        this.featureCount = featureCount;
        this.seeds = new ArrayList<>(seeds);
        this.perSeedMetrics = new ArrayList<>(perSeedMetrics);
        this.groupHoldoutMetrics = groupHoldoutMetrics;

        this.precision = MetricAggregate.collect(perSeedMetrics, Metrics::getPrecision);
        this.recall = MetricAggregate.collect(perSeedMetrics, Metrics::getRecall);
        this.f1 = MetricAggregate.collect(perSeedMetrics, Metrics::getF1);
        this.macroF1 = MetricAggregate.collect(perSeedMetrics, Metrics::getMacroF1);
        this.rocAuc = MetricAggregate.collect(perSeedMetrics, Metrics::getRocAuc);
        this.prAuc = MetricAggregate.collect(perSeedMetrics, Metrics::getPrAuc);
        this.fpr = MetricAggregate.collect(perSeedMetrics, Metrics::getFpr);
        this.accuracy = MetricAggregate.collect(perSeedMetrics, Metrics::getAccuracy);
        this.specificity = MetricAggregate.collect(perSeedMetrics, Metrics::getSpecificity);
        this.mcc = MetricAggregate.collect(perSeedMetrics, Metrics::getMcc);
    }

    public String getModelName() {
        return modelName;
    }

    public String getDescription() {
        return description;
    }

    public int getFeatureCount() {
        return featureCount;
    }

    public List<Long> getSeeds() {
        return seeds;
    }

    public List<Metrics> getPerSeedMetrics() {
        return perSeedMetrics;
    }

    public Metrics getGroupHoldoutMetrics() {
        return groupHoldoutMetrics;
    }

    public MetricAggregate getPrecision() {
        return precision;
    }

    public MetricAggregate getRecall() {
        return recall;
    }

    public MetricAggregate getF1() {
        return f1;
    }

    public MetricAggregate getMacroF1() {
        return macroF1;
    }

    public MetricAggregate getRocAuc() {
        return rocAuc;
    }

    public MetricAggregate getPrAuc() {
        return prAuc;
    }

    public MetricAggregate getFpr() {
        return fpr;
    }

    public MetricAggregate getAccuracy() {
        return accuracy;
    }

    public MetricAggregate getSpecificity() {
        return specificity;
    }

    public MetricAggregate getMcc() {
        return mcc;
    }

    /**
     * Confusion-matrix snapshot for the representative (first) seed; TP/TN/FP/FN are counts, so they
     * cannot be meaningfully averaged across seeds. Use {@link #getRepresentativeSeed()} for the label.
     */
    public Metrics getRepresentativeMetrics() {
        return perSeedMetrics.isEmpty() ? Metrics.empty() : perSeedMetrics.get(0);
    }

    public long getRepresentativeSeed() {
        return seeds.isEmpty() ? 0L : seeds.get(0);
    }

    public double getMeanF1() {
        return f1.getMean();
    }
}
