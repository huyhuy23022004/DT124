# DT124 - Offline Phishing Email Analysis from EML Files

Java 21 / Maven prototype that **reproduces the data schema and RQ1-RQ4 findings** of:

> Sharad Agarwal, Marie Vasek (2026). *Examining newly registered phishing domains at scale.*
> Journal of Cybersecurity, Vol. 12, Issue 1. DOI: `10.1093/cybsec/tyag020`

...and adds an **improved Weka ML model** that clearly outperforms the baseline, for the
Midterm (9-10 point) demonstration.

The entire pipeline is **100% offline and zero-execution**: `.eml` files are parsed from an
InputStream, URLs are never fetched, and attachments are only inspected in RAM (never written
to disk or executed).

## Prerequisites

- JDK 21 (Temurin 21 recommended)
- No Maven install required - the **Maven Wrapper** (`mvnw.cmd`) is bundled.

## Build & Test

```powershell
.\mvnw.cmd clean test        # compile + run the 38 JUnit 5 tests
```

## Run the live demo

```powershell
.\mvnw.cmd compile exec:java
```

The demo:

1. generates a labelled corpus of `benign` + `phishing` `.eml` files into `data/samples/`
   (no need to download the 1.7 GB Enron archive to see results),
2. parses and extracts all features offline (Parser Coverage baseline is `100%` on generated data),
3. reproduces the tyag020 RQ1-RQ4 statistics (paper benchmark vs offline proxy),
4. trains and compares **Baseline / Paper URL-Only / Proposed Hybrid** models with Weka,
5. prints the **ablation**, error analysis, cost metrics and writes artifacts to `artifacts/`.

## Project layout

```
pom.xml                         Maven build (Jakarta Mail, jsoup, commons-csv, Jackson, Weka, JUnit 5)
mvnw / mvnw.cmd / .mvn/         Maven Wrapper (self-contained)
configs/experiment_config.json  seeds, folds, counts, paths
rules/safe_indicators.yar       YARA-style safe indicator rules
data/samples/                   generated .eml corpus (benign & phishing)
data/manifests/                 SHA-256 data manifest + provenance
artifacts/reports/              CSV / JSON experiment results (incl. confusion_matrix.csv)
artifacts/figures/               chart PNGs used by the presentation deck
assets/paper/                    exact paper evidence crops rendered from tyag020.pdf
tools/slidegen/                  standalone POI generator for Slide/DT124_Bao_Cao_Giua_Ky_v2.pptx
src/main/java/com/dt124/
  config/     AppConfig
  model/      PaperDomainRecord, EmailFeatureRecord, AuthResult, AttachmentMeta, ParsedEmail
  parser/     OfflineEmlParser
  extractor/  AuthHeaderValidator, UrlDomainAnalyzer, SafeAttachmentInspector,
              SafeIndicatorRuleEngine, EmailFeatureExtractor
  dataset/    SyntheticEmailGenerator, DatasetLoader, Deduplicator, DataManifestService,
              GroupBasedSplitter, ArffBuilder
  ml/         FeatureSchema, ClassifierFactory, HybridScoringClassifier, ModelExperimentRunner,
              AblationStudyRunner, Metrics, PaperReplicationService, ErrorAnalyzer
  report/     TableFormatter, CsvReportWriter, JsonReportWriter
  MainApp.java
src/test/java/com/dt124/        JUnit 5 unit + integration + negative/security tests
```

## Using the real Enron dataset

Place an extracted copy of `enron_mail_20150507` under `data/raw/enron/` (and optional public
phishing `.eml` under `data/raw/phishing_eml/`) and re-run the demo. Files are loaded
recursively (extension-less maildir files included), deduplicated by `SHA-256(subject+body)`
and `Message-ID`, and recorded in `data/manifests/checksums_sha256.txt`.

## Feature groups (Proposed model)

| Group | Examples |
|---|---|
| Header / Auth | `received_hops`, `spf_fail`, `dkim_fail`, `dmarc_fail`, `from_return_path_mismatch`, `from_reply_to_mismatch` |
| Paper tyag020 URL/Domain | `url_count`, `url_ip_count`, `url_free_hosting_abuse`, `url_suspicious_tld`, `url_brand_hyphen`, `url_max_subdomains`, `domain_entropy_max`, `html_anchor_mismatch` |
| Safe Attachment | `att_count`, `att_dangerous_ext`, `att_double_ext`, `att_magic_mismatch`, `att_max_entropy` |
| YARA-style indicators | `yara_eval_obfuscation`, `yara_base64_blob`, `yara_hidden_login_form`, `yara_onload_js`, `yara_total_hits` |

Absolute `Year` is intentionally **excluded** to prevent temporal leakage between Enron
(1999-2002) and modern phishing mail; train/test splits are **group-disjoint** by sender domain.

## Evaluation metrics

Required metrics (positive class = phishing): Precision, Recall, Phishing F1, Macro-F1,
ROC-AUC, PR-AUC, FPR and Accuracy, each aggregated over folds/seeds as **mean ± SD and 95% CI**.

Supplementary security metrics (also reported): **TP / TN / FP / FN**, **Specificity**
(`TNR = 1 - FPR`) and **MCC**. They make the false-alarm cost explicit - important for a
security detector - and MCC gives an imbalance-robust single summary. TP/TN/FP/FN are counts
and therefore reported per seed (representative seed `42` in the slides); specificity and MCC
are additionally aggregated as mean across seeds.

Artifacts:

- `artifacts/reports/model_comparison.csv` - aggregated metrics incl. `specificity_mean`, `mcc_mean`
- `artifacts/reports/per_seed_metrics.csv` - per-seed metrics incl. `specificity`, `mcc`, `tp..fn`
- `artifacts/reports/confusion_matrix.csv` - `model, seed, tp, tn, fp, fn, specificity, mcc`
- `artifacts/reports/ablation.csv` - same columns for every ablation configuration

## Presentation deck

`Slide/DT124_Bao_Cao_Giua_Ky_v2.pptx` is a **new** 11-slide deck (the original deck is left
untouched). It embeds tables from `artifacts/reports/*.csv`, the PNGs in `artifacts/figures/`,
and exact paper evidence rendered from `tyag020.pdf` in `assets/paper/` (page 1 title/abstract,
Figures 1-2, and Table 12 - GSB detection). Detailed explanations moved off the slides are stored
as speaker notes on every slide.

A 5-minute speaker script matching the 11 slides is provided as
`Slide/DT124_Kich_Ban_5_Phut.md` and `Slide/DT124_Kich_Ban_5_Phut.docx`
(timing table + per-slide lines + quick Q&A + presentation tips).
Regenerate it with:

```powershell
.\mvnw.cmd -f tools\slidegen\pom.xml compile exec:java
```

Note when cloning: `tyag020.pdf` and `artifacts/` are intentionally not committed.
`assets/paper/` already contains the exact paper crops used by the deck; to re-render
them from the PDF, place `tyag020.pdf` in the repository root. Run the main demo once
before regenerating the deck so that `artifacts/reports/` exists.

## Safety & ethics

- Offline only. No WHOIS / DNS / CT-logs / Google Safe Browsing / HTTP requests are issued.
- Attachments are read into RAM with a 5 MB cap (DoS / zip-bomb guard) and never written or run.
- PII (emails, URLs, IPs) is redacted in all emitted logs/artifacts via `LogSanitizer`.
