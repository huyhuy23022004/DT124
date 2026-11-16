package com.dt124.report;

import com.dt124.ml.ExperimentResult;
import com.dt124.ml.MetricAggregate;
import com.dt124.ml.Metrics;
import com.dt124.ml.ReplicationReport;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Writes raw and aggregated experiment results to CSV using commons-csv.
 */
public final class CsvReportWriter {

    private static final String[] HEADERS = {
            "model", "features", "precision_mean", "precision_sd", "recall_mean", "recall_sd",
            "f1_mean", "f1_sd", "macro_f1_mean", "roc_auc_mean", "pr_auc_mean", "fpr_mean",
            "accuracy_mean", "specificity_mean", "mcc_mean", "mcc_sd", "f1_ci95", "group_holdout_f1"
    };

    public void writeModelComparison(Path output, List<ExperimentResult> results) throws IOException {
        createParent(output);
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer,
                     CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {
            for (ExperimentResult result : results) {
                printer.printRecord(
                        result.getModelName(),
                        result.getFeatureCount(),
                        round(result.getPrecision().getMean()), round(result.getPrecision().getSd()),
                        round(result.getRecall().getMean()), round(result.getRecall().getSd()),
                        round(result.getF1().getMean()), round(result.getF1().getSd()),
                        round(result.getMacroF1().getMean()),
                        round(result.getRocAuc().getMean()),
                        round(result.getPrAuc().getMean()),
                        round(result.getFpr().getMean()),
                        round(result.getAccuracy().getMean()),
                        round(result.getSpecificity().getMean()),
                        round(result.getMcc().getMean()),
                        round(result.getMcc().getSd()),
                        round(result.getF1().getCi95()),
                        result.getGroupHoldoutMetrics() == null ? ""
                                : round(result.getGroupHoldoutMetrics().getF1()));
            }
        }
    }

    public void writePerSeed(Path output, List<ExperimentResult> results) throws IOException {
        createParent(output);
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                      .setHeader("model", "seed", "precision", "recall", "f1", "macro_f1",
                              "roc_auc", "pr_auc", "fpr", "accuracy", "specificity", "mcc",
                              "tp", "tn", "fp", "fn").build())) {
            for (ExperimentResult result : results) {
                List<Long> seeds = result.getSeeds();
                List<Metrics> metrics = result.getPerSeedMetrics();
                for (int i = 0; i < seeds.size() && i < metrics.size(); i++) {
                    Metrics metric = metrics.get(i);
                    printer.printRecord(result.getModelName(), seeds.get(i),
                            round(metric.getPrecision()), round(metric.getRecall()),
                            round(metric.getF1()), round(metric.getMacroF1()),
                            round(metric.getRocAuc()), round(metric.getPrAuc()),
                            round(metric.getFpr()), round(metric.getAccuracy()),
                            round(metric.getSpecificity()), round(metric.getMcc()),
                            Math.round(metric.getTruePositives()), Math.round(metric.getTrueNegatives()),
                            Math.round(metric.getFalsePositives()), Math.round(metric.getFalseNegatives()));
                }
            }
        }
    }

    /**
     * Writes the security-oriented confusion-matrix table (TP/TN/FP/FN/Specificity/MCC) per seed.
     */
    public void writeConfusionMatrix(Path output, List<ExperimentResult> results) throws IOException {
        createParent(output);
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("model", "seed", "tp", "tn", "fp", "fn", "specificity", "mcc").build())) {
            for (ExperimentResult result : results) {
                List<Long> seeds = result.getSeeds();
                List<Metrics> metrics = result.getPerSeedMetrics();
                for (int i = 0; i < seeds.size() && i < metrics.size(); i++) {
                    Metrics metric = metrics.get(i);
                    printer.printRecord(result.getModelName(), seeds.get(i),
                            Math.round(metric.getTruePositives()), Math.round(metric.getTrueNegatives()),
                            Math.round(metric.getFalsePositives()), Math.round(metric.getFalseNegatives()),
                            round(metric.getSpecificity()), round(metric.getMcc()));
                }
            }
        }
    }

    public void writeReplication(Path output, ReplicationReport report) throws IOException {
        createParent(output);
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("metric", "value").build())) {
            printer.printRecord("emails_analysed", report.getEmailsAnalysed());
            printer.printRecord("unique_domains", report.getUniqueDomains());
            printer.printRecord("domains_with_https", report.getDomainsWithHttps());
            printer.printRecord("valid_tls_percent_proxy", round(report.getValidTlsPercent()));
            printer.printRecord("free_hosting_percent", round(report.getFreeHostingPercent()));
            printer.printRecord("brand_hyphen_percent", round(report.getBrandHyphenPercent()));
            printer.printRecord("ip_url_percent", round(report.getIpUrlPercent()));
            printer.printRecord("subdomain_phishing_percent", round(report.getSubdomainPhishingPercent()));
            printer.printRecord("avg_subdomain_depth", round(report.getAvgSubdomainDepth()));
            printer.printRecord("max_subdomain_depth", report.getMaxSubdomainDepth());
        }
    }

    public void writeTldDistribution(Path output, Map<String, Integer> tldCounts) throws IOException {
        createParent(output);
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("tld", "count").build())) {
            tldCounts.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .forEach(entry -> {
                        try {
                            printer.printRecord(entry.getKey(), entry.getValue());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private static double round(double value) {
        return Math.round(value * 10000.0d) / 10000.0d;
    }

    private static void createParent(Path output) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
    }

    public static String summaryLine(ExperimentResult result) {
        MetricAggregate f1 = result.getF1();
        return result.getModelName() + " F1=" + f1.formatted();
    }
}
