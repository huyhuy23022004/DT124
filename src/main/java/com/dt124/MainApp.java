package com.dt124;

import com.dt124.config.AppConfig;
import com.dt124.dataset.ArffBuilder;
import com.dt124.dataset.DataManifestService;
import com.dt124.dataset.DatasetLoader;
import com.dt124.dataset.Deduplicator;
import com.dt124.dataset.LoadStatistics;
import com.dt124.dataset.SyntheticEmailGenerator;
import com.dt124.extractor.EmailFeatureExtractor;
import com.dt124.extractor.ExtractionResult;
import com.dt124.extractor.SafeIndicatorRuleEngine;
import com.dt124.ml.AblationStudyRunner;
import com.dt124.ml.ClassifierFactory;
import com.dt124.ml.ErrorAnalyzer;
import com.dt124.ml.ExperimentResult;
import com.dt124.ml.Metrics;
import com.dt124.ml.ModelDefinition;
import com.dt124.ml.ModelExperimentRunner;
import com.dt124.ml.PaperReplicationService;
import com.dt124.ml.ReplicationReport;
import com.dt124.model.EmailFeatureRecord;
import com.dt124.parser.OfflineEmlParser;
import com.dt124.report.CsvReportWriter;
import com.dt124.report.JsonReportWriter;
import com.dt124.report.TableFormatter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * DT124 demo entry point. Runs the complete offline pipeline end to end:
 * <ol>
 *   <li>generates a controllable .eml corpus (Enron-style benign + tyag020-style phishing),</li>
 *   <li>parses and extracts features fully offline (zero-execution),</li>
 *   <li>reproduces the paper's RQ1-RQ4 statistics (paper benchmark vs offline proxy),</li>
 *   <li>trains and compares Baseline vs Paper-URL vs Proposed models with Weka,</li>
 *   <li>prints ablation, error analysis, coverage and cost metrics and writes artifacts.</li>
 * </ol>
 */
public final class MainApp {

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        AppConfig config = AppConfig.load(root.resolve("configs/experiment_config.json"));

        banner();

        SafeIndicatorRuleEngine ruleEngine = new SafeIndicatorRuleEngine(root.resolve(config.rulesPath));
        System.out.println("[rules] loaded " + ruleEngine.ruleNames().size()
                + " YARA-style rules from " + config.rulesPath + ": " + ruleEngine.ruleNames());

        Path samplesDir = root.resolve(config.samplesDir);
        Path benignDir = samplesDir.resolve("benign");
        Path phishingDir = samplesDir.resolve("phishing");
        ensureSamples(config, benignDir, phishingDir);

        OfflineEmlParser parser = new OfflineEmlParser();
        LoadStatistics stats = new LoadStatistics();
        List<ExtractionResult> allResults = new ArrayList<>();

        long extractStart = System.nanoTime();

        DatasetLoader benignLoader = new DatasetLoader(parser,
                new EmailFeatureExtractor(ruleEngine, "synthetic-benign", EmailFeatureRecord.LABEL_BENIGN));
        allResults.addAll(benignLoader.load(benignDir, "synthetic-benign",
                EmailFeatureRecord.LABEL_BENIGN, true, 0, stats));

        DatasetLoader phishingLoader = new DatasetLoader(parser,
                new EmailFeatureExtractor(ruleEngine, "synthetic-phishing", EmailFeatureRecord.LABEL_PHISHING));
        allResults.addAll(phishingLoader.load(phishingDir, "synthetic-phishing",
                EmailFeatureRecord.LABEL_PHISHING, true, 0, stats));

        Path enronDir = root.resolve(config.enronDir);
        if (Files.isDirectory(enronDir)) {
            DatasetLoader enronLoader = new DatasetLoader(parser,
                    new EmailFeatureExtractor(ruleEngine, "enron", EmailFeatureRecord.LABEL_BENIGN));
            allResults.addAll(enronLoader.load(enronDir, "enron", EmailFeatureRecord.LABEL_BENIGN,
                    true, config.maxEnronFiles, stats));
        }

        Path publicPhishingDir = root.resolve(config.phishingDir);
        if (Files.isDirectory(publicPhishingDir)) {
            DatasetLoader publicLoader = new DatasetLoader(parser,
                    new EmailFeatureExtractor(ruleEngine, "public-phishing", EmailFeatureRecord.LABEL_PHISHING));
            allResults.addAll(publicLoader.load(publicPhishingDir, "public-phishing",
                    EmailFeatureRecord.LABEL_PHISHING, true, 0, stats));
        }

        int duplicates = new Deduplicator().deduplicate(allResults);
        stats.setDuplicatesRemoved(duplicates);

        long extractMillis = (System.nanoTime() - extractStart) / 1_000_000L;

        List<EmailFeatureRecord> records = new ArrayList<>();
        for (ExtractionResult result : allResults) {
            records.add(result.getFeatureRecord());
        }

        printCoverage(stats, records, extractMillis);

        writeManifest(config, root, benignDir, phishingDir, enronDir, publicPhishingDir);

        if (!hasBothClasses(records)) {
            System.err.println("[fatal] Dataset must contain both benign and phishing samples. Aborting experiments.");
            return;
        }

        ReplicationReport replication = printReplication(allResults);
        List<ExperimentResult> modelResults = runModelComparison(config, records);
        List<ExperimentResult> ablationResults = runAblation(config, records);
        printErrorAnalysis(config, records);
        writeArtifacts(config, root, modelResults, ablationResults, replication);
        printVerdict(modelResults);
    }

    private static void banner() {
        System.out.println();
        System.out.println("########################################################################");
        System.out.println("#  DT124 - Offline Phishing Email Analysis from EML Files (Java/Maven) #");
        System.out.println("#  Reproducing: Agarwal & Vasek (2026), DOI 10.1093/cybsec/tyag020      #");
        System.out.println("#  Fully offline / zero-execution static analysis + improved ML model   #");
        System.out.println("########################################################################");
    }

    private static void ensureSamples(AppConfig config, Path benignDir, Path phishingDir) throws Exception {
        boolean needGenerate = config.regenerateSamples || !Files.isDirectory(benignDir)
                || !Files.isDirectory(phishingDir) || isEmpty(benignDir) || isEmpty(phishingDir);
        if (!needGenerate) {
            System.out.println("[samples] using existing corpus in " + benignDir.getParent());
            return;
        }
        System.out.println("[samples] generating " + config.benignCount + " benign + "
                + config.phishingCount + " phishing .eml files (seed=" + config.generatorSeed + ")");
        SyntheticEmailGenerator generator = new SyntheticEmailGenerator(config.generatorSeed);
        var report = generator.generate(benignDir, phishingDir, config.benignCount, config.phishingCount);
        System.out.println("[samples] wrote " + report.getTotal() + " files to " + benignDir.getParent());
    }

    private static boolean isEmpty(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) {
            return true;
        }
        try (var stream = Files.list(dir)) {
            return !stream.findAny().isPresent();
        }
    }

    private static void printCoverage(LoadStatistics stats, List<EmailFeatureRecord> records, long extractMillis) {
        System.out.println(TableFormatter.section("Parser Coverage & Dataset Quality"));
        int benign = 0;
        int phishing = 0;
        for (EmailFeatureRecord record : records) {
            if (record.getLabel() == EmailFeatureRecord.LABEL_PHISHING) {
                phishing++;
            } else {
                benign++;
            }
        }
        System.out.println("Total .eml files loaded : " + stats.getTotalFiles());
        System.out.println("Parsed successfully    : " + stats.getParsedOk());
        System.out.println("Parse failures         : " + stats.getFailed());
        System.out.println("Duplicates removed     : " + stats.getDuplicatesRemoved());
        System.out.printf(Locale.ROOT, "Parser Coverage        : %.2f%%%n", stats.getCoveragePercent());
        System.out.println("Benign / Phishing      : " + benign + " / " + phishing);
        if (!records.isEmpty()) {
            double perEmail = (double) extractMillis / records.size();
            System.out.printf(Locale.ROOT, "Extraction time        : %d ms total (%.3f ms/email)%n",
                    extractMillis, perEmail);
        }
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        System.out.println("Heap used (approx.)    : " + usedMb + " MB");
        System.out.println("Per-corpus parse       : " + stats.getPerCorpusParsed() + " of " + stats.getPerCorpus());
    }

    private static void writeManifest(AppConfig config, Path root, Path benignDir, Path phishingDir,
                                      Path enronDir, Path publicPhishingDir) throws Exception {
        DataManifestService manifestService = new DataManifestService();
        List<DataManifestService.DatasetEntry> entries = new ArrayList<>();
        entries.add(new DataManifestService.DatasetEntry("dt124-synthetic-samples", benignDir.getParent(),
                "generated-" + config.generatorSeed, "Synthetic test data (public domain)"));
        if (Files.isDirectory(enronDir)) {
            entries.add(new DataManifestService.DatasetEntry("enron-email-dataset", enronDir,
                    "enron_mail_20150507", "CMU / Enron - research use"));
        }
        if (Files.isDirectory(publicPhishingDir)) {
            entries.add(new DataManifestService.DatasetEntry("public-phishing-eml", publicPhishingDir,
                    "public", "Public labeled phishing samples"));
        }
        manifestService.writeManifest(root.resolve(config.manifestPath), entries);
        System.out.println("[manifest] wrote " + config.manifestPath);
    }

    private static ReplicationReport printReplication(List<ExtractionResult> allResults) {
        List<ExtractionResult> phishing = new ArrayList<>();
        for (ExtractionResult result : allResults) {
            if (result.getFeatureRecord().getLabel() == EmailFeatureRecord.LABEL_PHISHING) {
                phishing.add(result);
            }
        }
        PaperReplicationService service = new PaperReplicationService();
        ReplicationReport report = service.replicate(phishing);

        System.out.println(TableFormatter.section("Experiment A: Reproducing tyag020 RQ1-RQ4 (Paper vs Offline Proxy)"));

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"RQ1", "Newly registered phishing domains analysed",
                String.valueOf(PaperReplicationService.PAPER_NRD_COUNT) + " (11 months)",
                report.getUniqueDomains() + " unique hosts / " + report.getEmailsAnalysed() + " emails"});
        rows.add(new String[]{"RQ1", "Phishing NRDs with valid TLS (%)",
                fmt(PaperReplicationService.PAPER_VALID_TLS_PERCENT),
                fmt(report.getValidTlsPercent()) + " (https-URL proxy)"});
        rows.add(new String[]{"RQ1", "Domains with >50 TLS certs",
                String.valueOf(PaperReplicationService.PAPER_DOMAINS_OVER_50_CERTS),
                "n/a (offline CT logs unavailable)"});
        rows.add(new String[]{"RQ1", "Abuse of Cloudflare proxy (%)",
                fmt(PaperReplicationService.PAPER_CLOUDFLARE_PROXY_PERCENT),
                "n/a (needs live DNS/ASN)"});
        rows.add(new String[]{"RQ1", "Abuse of free web hosting (%)",
                "reported (Firebase web.app)", fmt(report.getFreeHostingPercent())});
        rows.add(new String[]{"RQ2", "Brand-impersonation + hyphen domains (%)",
                "qualitative (pay-ap[.]com)", fmt(report.getBrandHyphenPercent())});
        rows.add(new String[]{"RQ2", "Top targeted sector",
                "Delivery > Financial > Tech", topSector(report)});
        rows.add(new String[]{"RQ3", "Mean active lifetime (days)",
                fmt(PaperReplicationService.PAPER_MEAN_LIFETIME_DAYS) + " (vs 21h URL)",
                "n/a (needs longitudinal DNS)"});
        rows.add(new String[]{"RQ3", "Re-registered domains (%)",
                fmt(PaperReplicationService.PAPER_REREGISTERED_PERCENT),
                "n/a (needs historical WHOIS)"});
        rows.add(new String[]{"RQ4", "GSB subdomain blind spot (% emails on subdomains)",
                "API vs browser disagreement", fmt(report.getSubdomainPhishingPercent())});
        rows.add(new String[]{"RQ1", "URLs using raw IP literals (%)", "n/a",
                fmt(report.getIpUrlPercent())});
        rows.add(new String[]{"RQ1", "Mean / max subdomain depth", "n/a",
                fmt(report.getAvgSubdomainDepth()) + " / " + report.getMaxSubdomainDepth()});
        System.out.print(TableFormatter.table(
                new String[]{"RQ", "Finding", "Paper (tyag020)", "DT124 offline proxy"}, rows));

        if (!report.getTldCounts().isEmpty()) {
            System.out.println("Top TLDs abused in the offline corpus: " + topEntries(report.getTldCounts(), 8));
        }
        System.out.println("Note: fields marked n/a genuinely require online WHOIS/DNS/CT/GSB lookups that are");
        System.out.println("      prohibited offline; the offline proxies above are computed only from .eml content.");
        return report;
    }

    private static String topSector(ReplicationReport report) {
        return report.getSectorCounts().entrySet().stream()
                .max((a, b) -> Integer.compare(a.getValue(), b.getValue()))
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .orElse("n/a");
    }

    private static List<ExperimentResult> runModelComparison(AppConfig config, List<EmailFeatureRecord> records) {
        System.out.println(TableFormatter.section("Experiment B: Baseline vs Paper URL-Only vs Proposed Model"));
        System.out.println("Protocol: " + config.folds + "-fold cross-validation, seeds=" + config.seeds
                + ", plus group-disjoint holdout (" + (int) (config.groupHoldoutFraction * 100) + "%).");

        ModelExperimentRunner runner = new ModelExperimentRunner(
                config.folds, config.seedArray(), config.groupHoldoutFraction);
        List<ExperimentResult> results = runner.runAll(ClassifierFactory.allThree(), records);

        List<String[]> rows = new ArrayList<>();
        for (ExperimentResult result : results) {
            rows.add(new String[]{
                    result.getModelName(),
                    String.valueOf(result.getFeatureCount()),
                    fmt(result.getPrecision().getMean()),
                    fmt(result.getRecall().getMean()),
                    fmt(result.getF1().getMean()),
                    fmt(result.getMacroF1().getMean()),
                    fmt(result.getRocAuc().getMean()),
                    fmt(result.getPrAuc().getMean()),
                    fmt(result.getFpr().getMean()),
                    fmt(result.getSpecificity().getMean())
            });
        }
        System.out.print(TableFormatter.table(new String[]{
                "Model", "#Feat", "Precision", "Recall", "F1", "MacroF1", "ROC-AUC", "PR-AUC", "FPR", "Spec"},
                rows));

        System.out.println("\nF1 mean +/- SD with 95% CI:");
        for (ExperimentResult result : results) {
            System.out.println("  - " + result.getModelName() + " : " + result.getF1().formatted());
        }

        System.out.println("\nPer-seed phishing F1:");
        for (ExperimentResult result : results) {
            StringBuilder sb = new StringBuilder("  - " + result.getModelName() + " : ");
            List<Metrics> metrics = result.getPerSeedMetrics();
            for (int i = 0; i < result.getSeeds().size() && i < metrics.size(); i++) {
                sb.append(String.format(Locale.ROOT, "seed%d=%.3f  ", result.getSeeds().get(i), metrics.get(i).getF1()));
            }
            System.out.println(sb);
        }

        System.out.println("\nGroup-disjoint holdout F1 (leakage-free):");
        for (ExperimentResult result : results) {
            String value = result.getGroupHoldoutMetrics() == null ? "n/a"
                    : fmt(result.getGroupHoldoutMetrics().getF1());
            System.out.println("  - " + result.getModelName() + " : " + value);
        }

        System.out.println("\nConfusion matrix & security metrics (representative seed "
                + results.get(0).getRepresentativeSeed() + ", positive=phishing):");
        List<String[]> cmRows = new ArrayList<>();
        for (ExperimentResult result : results) {
            Metrics m = result.getRepresentativeMetrics();
            cmRows.add(new String[]{
                    result.getModelName(),
                    String.valueOf(Math.round(m.getTruePositives())),
                    String.valueOf(Math.round(m.getTrueNegatives())),
                    String.valueOf(Math.round(m.getFalsePositives())),
                    String.valueOf(Math.round(m.getFalseNegatives())),
                    fmt(m.getSpecificity()),
                    fmt(m.getMcc())
            });
        }
        System.out.print(TableFormatter.table(
                new String[]{"Model", "TP", "TN", "FP", "FN", "Specificity", "MCC"}, cmRows));
        return results;
    }

    private static List<ExperimentResult> runAblation(AppConfig config, List<EmailFeatureRecord> records) {
        System.out.println(TableFormatter.section("Experiment C: Ablation Study"));
        ModelExperimentRunner runner = new ModelExperimentRunner(
                config.folds, config.seedArray(), config.groupHoldoutFraction);
        List<ExperimentResult> results = new AblationStudyRunner(runner).run(records);

        List<String[]> rows = new ArrayList<>();
        for (ExperimentResult result : results) {
            rows.add(new String[]{
                    result.getModelName(),
                    String.valueOf(result.getFeatureCount()),
                    fmt(result.getPrecision().getMean()),
                    fmt(result.getRecall().getMean()),
                    fmt(result.getF1().getMean()),
                    fmt(result.getRocAuc().getMean()),
                    fmt(result.getPrAuc().getMean()),
                    fmt(result.getSpecificity().getMean()),
                    fmt(result.getMcc().getMean())
            });
        }
        System.out.print(TableFormatter.table(new String[]{
                "Configuration", "#Feat", "Precision", "Recall", "F1", "ROC-AUC", "PR-AUC",
                "Spec", "MCC"}, rows));
        return results;
    }

    private static void printErrorAnalysis(AppConfig config, List<EmailFeatureRecord> records) {
        System.out.println(TableFormatter.section("Experiment D: Error Analysis (False Positives / Negatives)"));
        ModelDefinition proposed = ClassifierFactory.proposedFull();
        List<String> lines = new ErrorAnalyzer().analyze(proposed, records,
                config.groupHoldoutFraction, config.seedArray()[0]);
        if (lines.isEmpty()) {
            System.out.println("No misclassifications in the group holdout.");
        } else {
            for (String line : lines) {
                System.out.println("  " + line);
            }
        }
    }

    private static void writeArtifacts(AppConfig config, Path root, List<ExperimentResult> modelResults,
                                       List<ExperimentResult> ablationResults, ReplicationReport replication)
            throws Exception {
        Path reports = root.resolve(config.artifactsDir).resolve("reports");
        Files.createDirectories(reports);

        CsvReportWriter csv = new CsvReportWriter();
        csv.writeModelComparison(reports.resolve("model_comparison.csv"), modelResults);
        csv.writePerSeed(reports.resolve("per_seed_metrics.csv"), modelResults);
        csv.writeModelComparison(reports.resolve("ablation.csv"), ablationResults);
        csv.writeConfusionMatrix(reports.resolve("confusion_matrix.csv"), modelResults);
        csv.writeReplication(reports.resolve("replication.csv"), replication);
        csv.writeTldDistribution(reports.resolve("tld_distribution.csv"), replication.getTldCounts());

        JsonReportWriter json = new JsonReportWriter();
        json.writeExperiments(reports.resolve("experiments.json"), modelResults,
                config.folds, config.seedArray());
        json.writeReplication(reports.resolve("replication.json"), replication);

        System.out.println("\n[artifacts] wrote reports to " + reports);
    }

    private static void printVerdict(List<ExperimentResult> results) {
        System.out.println(TableFormatter.section("Result: Proposed vs Baseline"));
        ExperimentResult baseline = results.get(0);
        ExperimentResult proposed = results.get(results.size() - 1);
        double deltaF1 = proposed.getF1().getMean() - baseline.getF1().getMean();
        double deltaRoc = proposed.getRocAuc().getMean() - baseline.getRocAuc().getMean();
        double deltaPr = proposed.getPrAuc().getMean() - baseline.getPrAuc().getMean();
        System.out.printf(Locale.ROOT, "Baseline  F1=%.4f  ROC-AUC=%.4f  PR-AUC=%.4f%n",
                baseline.getF1().getMean(), baseline.getRocAuc().getMean(), baseline.getPrAuc().getMean());
        System.out.printf(Locale.ROOT, "Proposed  F1=%.4f  ROC-AUC=%.4f  PR-AUC=%.4f%n",
                proposed.getF1().getMean(), proposed.getRocAuc().getMean(), proposed.getPrAuc().getMean());
        System.out.printf(Locale.ROOT, "Delta     F1=%+.4f  ROC-AUC=%+.4f  PR-AUC=%+.4f%n",
                deltaF1, deltaRoc, deltaPr);
        boolean improved = deltaF1 > 0 && deltaRoc >= 0;
        System.out.println(improved
                ? "==> The Proposed Hybrid model OUTPERFORMS the Baseline (target for the 9-10 point demo)."
                : "==> NOTE: Proposed model did not beat the baseline on this run; inspect artifacts/reports.");
    }

    private static boolean hasBothClasses(List<EmailFeatureRecord> records) {
        boolean benign = false;
        boolean phishing = false;
        for (EmailFeatureRecord record : records) {
            if (record.getLabel() == EmailFeatureRecord.LABEL_PHISHING) {
                phishing = true;
            } else {
                benign = true;
            }
        }
        return benign && phishing;
    }

    private static String topEntries(java.util.Map<String, Integer> map, int limit) {
        return map.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> "." + e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
