package com.dt124.ml;

import com.dt124.extractor.ExtractionResult;
import com.dt124.extractor.UrlDomainAnalyzer;
import com.dt124.model.PaperDomainRecord;
import com.dt124.util.EntropyCalculator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reproduces the data schema and the headline RQ1-RQ4 statistics of Agarwal &amp; Vasek (2026),
 * "Examining newly registered phishing domains at scale" (Journal of Cybersecurity, tyag020).
 *
 * <p>Because the paper's original 15,126-domain dataset is closed-source (limitation 4), this
 * service (a) prints the paper's published benchmark constants verbatim and (b) computes the
 * offline observable proxies on the local .eml corpus so the two can be compared side by side.</p>
 */
public final class PaperReplicationService {

    // ---- Paper benchmark constants (tyag020, Journal of Cybersecurity 2026) ----
    public static final int PAPER_NRD_COUNT = 15126;
    public static final int PAPER_COLLECTION_MONTHS = 11;
    public static final double PAPER_VALID_TLS_PERCENT = 79.3d;
    public static final double PAPER_GENERAL_TLS_PERCENT = 66.7d;
    public static final int PAPER_DOMAINS_OVER_50_CERTS = 190;
    public static final double PAPER_CLOUDFLARE_PROXY_PERCENT = 26.0d;
    public static final double PAPER_MEAN_LIFETIME_DAYS = 8.6d;
    public static final double PAPER_TYPICAL_URL_LIFETIME_HOURS = 21.0d;
    public static final double PAPER_REREGISTERED_PERCENT = 25.7d;

    private static final Map<String, String> BRAND_SECTOR = new LinkedHashMap<>();

    static {
        putSector("delivery", "dhl", "usps", "fedex", "ups", "dpd", "royalmail");
        putSector("financial", "paypal", "chase", "wellsfargo", "hsbc", "citibank", "visa",
                "mastercard", "americanexpress", "revolut", "bankofamerica", "santander",
                "coinbase", "binance");
        putSector("tech", "apple", "microsoft", "google", "amazon", "netflix", "adobe", "dropbox",
                "linkedin", "spotify", "steam", "facebook", "instagram", "whatsapp", "tiktok",
                "telegram", "office365", "outlook");
    }

    private static void putSector(String sector, String... brands) {
        for (String brand : brands) {
            BRAND_SECTOR.put(brand, sector);
        }
    }

    /**
     * Builds the replication report from phishing (and optionally benign) extraction results.
     */
    public ReplicationReport replicate(List<ExtractionResult> phishingResults) {
        ReplicationReport report = new ReplicationReport();
        report.setEmailsAnalysed(phishingResults.size());

        Map<String, PaperDomainRecord> byDomain = new LinkedHashMap<>();
        int emailsWithHttps = 0;
        int emailsWithIpUrl = 0;
        int emailsWithSubdomain = 0;
        long totalSubdomainDepth = 0;

        for (ExtractionResult result : phishingResults) {
            var urls = result.getUrlAnalysis();
            boolean https = urls.getUrls().stream().anyMatch(u -> u.toLowerCase(Locale.ROOT).startsWith("https://"));
            if (https) {
                emailsWithHttps++;
            }
            if (urls.getIpUrlCount() > 0) {
                emailsWithIpUrl++;
            }
            if (urls.getMaxSubdomains() >= 1) {
                emailsWithSubdomain++;
            }
            totalSubdomainDepth += urls.getMaxSubdomains();

            for (String host : urls.getHosts()) {
                if (host.isEmpty() || byDomain.containsKey(host)) {
                    continue;
                }
                byDomain.put(host, toRecord(host, https, result.getFeatureRecord().getId()));
            }
        }

        report.setUniqueDomains(byDomain.size());
        report.getRecords().addAll(byDomain.values());

        int httpsDomains = 0;
        int freeHosting = 0;
        int brandHyphen = 0;
        int suspiciousTld = 0;
        for (PaperDomainRecord record : byDomain.values()) {
            if (record.isValidTls()) {
                httpsDomains++;
            }
            if (!record.getFreeHostingProvider().isEmpty()) {
                freeHosting++;
            }
            if (record.isBrandHyphen()) {
                brandHyphen++;
            }
            if (!record.getTld().isEmpty()) {
                report.getTldCounts().merge(record.getTld(), 1, Integer::sum);
            }
            if (UrlDomainAnalyzer.suspiciousTlds().contains(record.getTld())) {
                suspiciousTld++;
            }
            if (!record.getBrandSector().isEmpty()) {
                report.getSectorCounts().merge(record.getBrandSector(), 1, Integer::sum);
            }
        }

        report.setDomainsWithHttps(httpsDomains);
        int domainCount = Math.max(1, byDomain.size());
        report.setValidTlsPercent(100.0d * httpsDomains / domainCount);
        report.setFreeHostingPercent(100.0d * freeHosting / domainCount);
        report.setBrandHyphenPercent(100.0d * brandHyphen / domainCount);
        report.setIpUrlPercent(100.0d * emailsWithIpUrl / Math.max(1, phishingResults.size()));
        report.setSubdomainPhishingPercent(100.0d * emailsWithSubdomain / Math.max(1, phishingResults.size()));
        report.setAvgSubdomainDepth((double) totalSubdomainDepth / Math.max(1, phishingResults.size()));
        report.setMaxSubdomainDepth(byDomain.values().stream()
                .mapToInt(PaperDomainRecord::getSubdomainDepth).max().orElse(0));

        return report;
    }

    private PaperDomainRecord toRecord(String host, boolean httpsFromEmail, String emailId) {
        PaperDomainRecord record = new PaperDomainRecord();
        record.setDomain(host);
        record.setTld(UrlDomainAnalyzer.publicSuffix(host));
        record.setSubdomainDepth(UrlDomainAnalyzer.subdomainDepth(host));
        record.setDomainLength(host.length());
        record.setValidTls(httpsFromEmail);
        record.setSourceEmailId(emailId);

        String freeHosting = matchFreeHosting(host);
        record.setFreeHostingProvider(freeHosting);

        String brand = matchBrand(host);
        if (!brand.isEmpty()) {
            record.setBrandName(brand);
            record.setBrandSector(BRAND_SECTOR.getOrDefault(brand, "other"));
            record.setBrandHyphen(host.contains("-") && !brand.isEmpty());
        }

        String registrable = UrlDomainAnalyzer.registrableDomain(host);
        String label = registrable.contains(".") ? registrable.substring(0, registrable.indexOf('.')) : registrable;
        record.setLabelEntropy(EntropyCalculator.shannonEntropy(label));
        return record;
    }

    private String matchFreeHosting(String host) {
        for (String suffix : UrlDomainAnalyzer.freeHostingSuffixes()) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                return suffix;
            }
        }
        return "";
    }

    private String matchBrand(String host) {
        Set<String> brands = UrlDomainAnalyzer.brands();
        for (String brand : brands) {
            if (host.contains(brand)) {
                return brand;
            }
        }
        return "";
    }
}
