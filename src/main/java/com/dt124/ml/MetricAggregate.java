package com.dt124.ml;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Aggregate of a metric across repeated runs (seeds): mean, standard deviation and 95% confidence
 * interval.
 */
public final class MetricAggregate {

    private final double mean;
    private final double sd;
    private final double ci95;
    private final int n;

    private MetricAggregate(double mean, double sd, double ci95, int n) {
        this.mean = mean;
        this.sd = sd;
        this.ci95 = ci95;
        this.n = n;
    }

    public static MetricAggregate of(List<Double> values) {
        int n = values.size();
        if (n == 0) {
            return new MetricAggregate(0, 0, 0, 0);
        }
        double mean = 0.0d;
        for (double value : values) {
            mean += value;
        }
        mean /= n;
        double variance = 0.0d;
        for (double value : values) {
            variance += Math.pow(value - mean, 2);
        }
        double sd = n > 1 ? Math.sqrt(variance / (n - 1)) : 0.0d;
        double ci95 = n > 1 ? 1.96d * sd / Math.sqrt(n) : 0.0d;
        return new MetricAggregate(mean, sd, ci95, n);
    }

    public double getMean() {
        return mean;
    }

    public double getSd() {
        return sd;
    }

    public double getCi95() {
        return ci95;
    }

    public int getN() {
        return n;
    }

    public String formatted() {
        return String.format(Locale.ROOT, "%.4f ± %.4f (95%% CI ±%.4f)", mean, sd, ci95);
    }

    /**
     * Collects a single metric across a list of {@link Metrics}.
     */
    public static MetricAggregate collect(List<Metrics> metrics, MetricSelector selector) {
        List<Double> values = new ArrayList<>();
        for (Metrics metric : metrics) {
            values.add(selector.select(metric));
        }
        return of(values);
    }

    @FunctionalInterface
    public interface MetricSelector {
        double select(Metrics metrics);
    }
}
