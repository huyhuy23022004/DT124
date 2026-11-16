package com.dt124.extractor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Result of offline URL / domain analysis. Holds both the raw extracted URLs (useful for error
 * analysis and the paper-replication records) and the aggregated tyag020 feature values.
 */
public final class UrlAnalysis {

    private final Set<String> urls = new LinkedHashSet<>();
    private final Set<String> hosts = new LinkedHashSet<>();
    private final List<String> htmlAnchors = new ArrayList<>();

    private int ipUrlCount;
    private boolean freeHostingAbuse;
    private boolean suspiciousTld;
    private boolean brandHyphen;
    private int maxSubdomains;
    private double maxDomainEntropy;
    private boolean htmlAnchorMismatch;
    private String primaryHost = "";

    public Set<String> getUrls() {
        return urls;
    }

    public Set<String> getHosts() {
        return hosts;
    }

    public List<String> getHtmlAnchors() {
        return htmlAnchors;
    }

    public int getUrlCount() {
        return urls.size();
    }

    public int getIpUrlCount() {
        return ipUrlCount;
    }

    public void setIpUrlCount(int ipUrlCount) {
        this.ipUrlCount = ipUrlCount;
    }

    public boolean isFreeHostingAbuse() {
        return freeHostingAbuse;
    }

    public void setFreeHostingAbuse(boolean freeHostingAbuse) {
        this.freeHostingAbuse = freeHostingAbuse;
    }

    public boolean isSuspiciousTld() {
        return suspiciousTld;
    }

    public void setSuspiciousTld(boolean suspiciousTld) {
        this.suspiciousTld = suspiciousTld;
    }

    public boolean isBrandHyphen() {
        return brandHyphen;
    }

    public void setBrandHyphen(boolean brandHyphen) {
        this.brandHyphen = brandHyphen;
    }

    public int getMaxSubdomains() {
        return maxSubdomains;
    }

    public void setMaxSubdomains(int maxSubdomains) {
        this.maxSubdomains = maxSubdomains;
    }

    public double getMaxDomainEntropy() {
        return maxDomainEntropy;
    }

    public void setMaxDomainEntropy(double maxDomainEntropy) {
        this.maxDomainEntropy = maxDomainEntropy;
    }

    public boolean isHtmlAnchorMismatch() {
        return htmlAnchorMismatch;
    }

    public void setHtmlAnchorMismatch(boolean htmlAnchorMismatch) {
        this.htmlAnchorMismatch = htmlAnchorMismatch;
    }

    public String getPrimaryHost() {
        return primaryHost;
    }

    public void setPrimaryHost(String primaryHost) {
        this.primaryHost = primaryHost == null ? "" : primaryHost;
    }
}
