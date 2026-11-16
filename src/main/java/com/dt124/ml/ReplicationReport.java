package com.dt124.ml;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregated outcome of reproducing the paper's RQ1-RQ4 statistics on the offline .eml corpus.
 */
public final class ReplicationReport {

    private int emailsAnalysed;
    private int uniqueDomains;
    private int domainsWithHttps;
    private double validTlsPercent;
    private double freeHostingPercent;
    private double brandHyphenPercent;
    private double ipUrlPercent;
    private double subdomainPhishingPercent;
    private double avgSubdomainDepth;
    private int maxSubdomainDepth;
    private final Map<String, Integer> tldCounts = new LinkedHashMap<>();
    private final Map<String, Integer> sectorCounts = new LinkedHashMap<>();
    private final java.util.List<com.dt124.model.PaperDomainRecord> records = new java.util.ArrayList<>();

    public int getEmailsAnalysed() {
        return emailsAnalysed;
    }

    public void setEmailsAnalysed(int emailsAnalysed) {
        this.emailsAnalysed = emailsAnalysed;
    }

    public int getUniqueDomains() {
        return uniqueDomains;
    }

    public void setUniqueDomains(int uniqueDomains) {
        this.uniqueDomains = uniqueDomains;
    }

    public int getDomainsWithHttps() {
        return domainsWithHttps;
    }

    public void setDomainsWithHttps(int domainsWithHttps) {
        this.domainsWithHttps = domainsWithHttps;
    }

    public double getValidTlsPercent() {
        return validTlsPercent;
    }

    public void setValidTlsPercent(double validTlsPercent) {
        this.validTlsPercent = validTlsPercent;
    }

    public double getFreeHostingPercent() {
        return freeHostingPercent;
    }

    public void setFreeHostingPercent(double freeHostingPercent) {
        this.freeHostingPercent = freeHostingPercent;
    }

    public double getBrandHyphenPercent() {
        return brandHyphenPercent;
    }

    public void setBrandHyphenPercent(double brandHyphenPercent) {
        this.brandHyphenPercent = brandHyphenPercent;
    }

    public double getIpUrlPercent() {
        return ipUrlPercent;
    }

    public void setIpUrlPercent(double ipUrlPercent) {
        this.ipUrlPercent = ipUrlPercent;
    }

    public double getSubdomainPhishingPercent() {
        return subdomainPhishingPercent;
    }

    public void setSubdomainPhishingPercent(double subdomainPhishingPercent) {
        this.subdomainPhishingPercent = subdomainPhishingPercent;
    }

    public double getAvgSubdomainDepth() {
        return avgSubdomainDepth;
    }

    public void setAvgSubdomainDepth(double avgSubdomainDepth) {
        this.avgSubdomainDepth = avgSubdomainDepth;
    }

    public int getMaxSubdomainDepth() {
        return maxSubdomainDepth;
    }

    public void setMaxSubdomainDepth(int maxSubdomainDepth) {
        this.maxSubdomainDepth = maxSubdomainDepth;
    }

    public Map<String, Integer> getTldCounts() {
        return tldCounts;
    }

    public Map<String, Integer> getSectorCounts() {
        return sectorCounts;
    }

    public java.util.List<com.dt124.model.PaperDomainRecord> getRecords() {
        return records;
    }
}
