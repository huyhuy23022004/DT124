package com.dt124.report;

import com.dt124.ml.ExperimentResult;
import com.dt124.ml.MetricAggregate;
import com.dt124.ml.Metrics;
import com.dt124.ml.ReplicationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes structured experiment results to JSON using Jackson, keeping the full audit trail required
 * by the experiment protocol.
 */
public final class JsonReportWriter {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void writeExperiments(Path output, List<ExperimentResult> results,
                                 int folds, long[] seeds) throws IOException {
        createParent(output);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("folds", folds);
        root.put("seeds", seeds);

        List<Map<String, Object>> models = new ArrayList<>();
        for (ExperimentResult result : results) {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("name", result.getModelName());
            model.put("description", result.getDescription());
            model.put("featureCount", result.getFeatureCount());
            model.put("metrics", metricsMap(result));
            if (result.getGroupHoldoutMetrics() != null) {
                Map<String, Object> holdout = new LinkedHashMap<>();
                Metrics m = result.getGroupHoldoutMetrics();
                holdout.put("precision", m.getPrecision());
                holdout.put("recall", m.getRecall());
                holdout.put("f1", m.getF1());
                holdout.put("rocAuc", m.getRocAuc());
                holdout.put("prAuc", m.getPrAuc());
                holdout.put("fpr", m.getFpr());
                holdout.put("specificity", m.getSpecificity());
                holdout.put("mcc", m.getMcc());
                holdout.put("tp", m.getTruePositives());
                holdout.put("tn", m.getTrueNegatives());
                holdout.put("fp", m.getFalsePositives());
                holdout.put("fn", m.getFalseNegatives());
                model.put("groupHoldout", holdout);
            }
            List<Map<String, Object>> perSeed = new ArrayList<>();
            for (int i = 0; i < result.getSeeds().size(); i++) {
                Map<String, Object> seedEntry = new LinkedHashMap<>();
                seedEntry.put("seed", result.getSeeds().get(i));
                Metrics m = result.getPerSeedMetrics().get(i);
                seedEntry.put("precision", m.getPrecision());
                seedEntry.put("recall", m.getRecall());
                seedEntry.put("f1", m.getF1());
                seedEntry.put("rocAuc", m.getRocAuc());
                seedEntry.put("prAuc", m.getPrAuc());
                seedEntry.put("fpr", m.getFpr());
                seedEntry.put("accuracy", m.getAccuracy());
                seedEntry.put("specificity", m.getSpecificity());
                seedEntry.put("mcc", m.getMcc());
                seedEntry.put("tp", m.getTruePositives());
                seedEntry.put("tn", m.getTrueNegatives());
                seedEntry.put("fp", m.getFalsePositives());
                seedEntry.put("fn", m.getFalseNegatives());
                perSeed.add(seedEntry);
            }
            model.put("perSeed", perSeed);
            models.add(model);
        }
        root.put("models", models);
        mapper.writeValue(output.toFile(), root);
    }

    public void writeReplication(Path output, ReplicationReport report) throws IOException {
        createParent(output);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("emailsAnalysed", report.getEmailsAnalysed());
        root.put("uniqueDomains", report.getUniqueDomains());
        root.put("validTlsPercentProxy", report.getValidTlsPercent());
        root.put("freeHostingPercent", report.getFreeHostingPercent());
        root.put("brandHyphenPercent", report.getBrandHyphenPercent());
        root.put("ipUrlPercent", report.getIpUrlPercent());
        root.put("subdomainPhishingPercent", report.getSubdomainPhishingPercent());
        root.put("avgSubdomainDepth", report.getAvgSubdomainDepth());
        root.put("maxSubdomainDepth", report.getMaxSubdomainDepth());
        root.put("tldCounts", report.getTldCounts());
        root.put("sectorCounts", report.getSectorCounts());
        mapper.writeValue(output.toFile(), root);
    }

    private Map<String, Object> metricsMap(ExperimentResult result) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("precision", aggregate(result.getPrecision()));
        metrics.put("recall", aggregate(result.getRecall()));
        metrics.put("f1", aggregate(result.getF1()));
        metrics.put("macroF1", aggregate(result.getMacroF1()));
        metrics.put("rocAuc", aggregate(result.getRocAuc()));
        metrics.put("prAuc", aggregate(result.getPrAuc()));
        metrics.put("fpr", aggregate(result.getFpr()));
        metrics.put("accuracy", aggregate(result.getAccuracy()));
        metrics.put("specificity", aggregate(result.getSpecificity()));
        metrics.put("mcc", aggregate(result.getMcc()));
        metrics.put("confusion", confusionMap(result.getRepresentativeMetrics()));
        return metrics;
    }

    private Map<String, Object> confusionMap(Metrics metrics) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tp", metrics.getTruePositives());
        map.put("tn", metrics.getTrueNegatives());
        map.put("fp", metrics.getFalsePositives());
        map.put("fn", metrics.getFalseNegatives());
        map.put("specificity", metrics.getSpecificity());
        map.put("mcc", metrics.getMcc());
        return map;
    }

    private Map<String, Object> aggregate(MetricAggregate aggregate) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mean", aggregate.getMean());
        map.put("sd", aggregate.getSd());
        map.put("ci95", aggregate.getCi95());
        map.put("n", aggregate.getN());
        return map;
    }

    private static void createParent(Path output) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
    }
}
