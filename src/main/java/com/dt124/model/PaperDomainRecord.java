package com.dt124.model;

/**
 * Data schema reproducing the newly-registered-domain (NRD) records analysed in
 * Agarwal &amp; Vasek (2026), "Examining newly registered phishing domains at scale"
 * (Journal of Cybersecurity, DOI 10.1093/cybsec/tyag020).
 *
 * <p>Fields map directly to the four research questions (RQ1-RQ4) of the paper. These records are
 * produced offline from the safe, static indicators found inside .eml files; no live WHOIS / DNS /
 * CT-log / Google Safe Browsing requests are ever issued (paper limitation 1).</p>
 */
public final class PaperDomainRecord {

    // ---- RQ1: infrastructure, DNS, ASN and TLS ----
    private String domain = "";
    private String tld = "";
    private String registeredAt = "";
    private int tlsCertCount;
    private boolean validTls;
    private boolean cloudflareProxy;
    private String freeHostingProvider = "";
    private String asn = "";
    private double asnAgeYears = -1.0d;

    // ---- RQ2: brand impersonation ----
    private String brandName = "";
    private String brandSector = "";
    private boolean brandHyphen;
    private int domainLength;
    private double labelEntropy;

    // ---- RQ3: active lifetime and re-registration ----
    private double activeLifetimeDays = -1.0d;
    private boolean reregistered;
    private boolean priorMalicious;

    // ---- RQ4: Google Safe Browsing disagreement ----
    private int subdomainDepth;
    private boolean gsbApiFlagged;
    private boolean gsbBrowserFlagged;

    private String sourceEmailId = "";

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain == null ? "" : domain;
    }

    public String getTld() {
        return tld;
    }

    public void setTld(String tld) {
        this.tld = tld == null ? "" : tld;
    }

    public String getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(String registeredAt) {
        this.registeredAt = registeredAt == null ? "" : registeredAt;
    }

    public int getTlsCertCount() {
        return tlsCertCount;
    }

    public void setTlsCertCount(int tlsCertCount) {
        this.tlsCertCount = tlsCertCount;
    }

    public boolean isValidTls() {
        return validTls;
    }

    public void setValidTls(boolean validTls) {
        this.validTls = validTls;
    }

    public boolean isCloudflareProxy() {
        return cloudflareProxy;
    }

    public void setCloudflareProxy(boolean cloudflareProxy) {
        this.cloudflareProxy = cloudflareProxy;
    }

    public String getFreeHostingProvider() {
        return freeHostingProvider;
    }

    public void setFreeHostingProvider(String freeHostingProvider) {
        this.freeHostingProvider = freeHostingProvider == null ? "" : freeHostingProvider;
    }

    public String getAsn() {
        return asn;
    }

    public void setAsn(String asn) {
        this.asn = asn == null ? "" : asn;
    }

    public double getAsnAgeYears() {
        return asnAgeYears;
    }

    public void setAsnAgeYears(double asnAgeYears) {
        this.asnAgeYears = asnAgeYears;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName == null ? "" : brandName;
    }

    public String getBrandSector() {
        return brandSector;
    }

    public void setBrandSector(String brandSector) {
        this.brandSector = brandSector == null ? "" : brandSector;
    }

    public boolean isBrandHyphen() {
        return brandHyphen;
    }

    public void setBrandHyphen(boolean brandHyphen) {
        this.brandHyphen = brandHyphen;
    }

    public int getDomainLength() {
        return domainLength;
    }

    public void setDomainLength(int domainLength) {
        this.domainLength = domainLength;
    }

    public double getLabelEntropy() {
        return labelEntropy;
    }

    public void setLabelEntropy(double labelEntropy) {
        this.labelEntropy = labelEntropy;
    }

    public double getActiveLifetimeDays() {
        return activeLifetimeDays;
    }

    public void setActiveLifetimeDays(double activeLifetimeDays) {
        this.activeLifetimeDays = activeLifetimeDays;
    }

    public boolean isReregistered() {
        return reregistered;
    }

    public void setReregistered(boolean reregistered) {
        this.reregistered = reregistered;
    }

    public boolean isPriorMalicious() {
        return priorMalicious;
    }

    public void setPriorMalicious(boolean priorMalicious) {
        this.priorMalicious = priorMalicious;
    }

    public int getSubdomainDepth() {
        return subdomainDepth;
    }

    public void setSubdomainDepth(int subdomainDepth) {
        this.subdomainDepth = subdomainDepth;
    }

    public boolean isGsbApiFlagged() {
        return gsbApiFlagged;
    }

    public void setGsbApiFlagged(boolean gsbApiFlagged) {
        this.gsbApiFlagged = gsbApiFlagged;
    }

    public boolean isGsbBrowserFlagged() {
        return gsbBrowserFlagged;
    }

    public void setGsbBrowserFlagged(boolean gsbBrowserFlagged) {
        this.gsbBrowserFlagged = gsbBrowserFlagged;
    }

    public String getSourceEmailId() {
        return sourceEmailId;
    }

    public void setSourceEmailId(String sourceEmailId) {
        this.sourceEmailId = sourceEmailId == null ? "" : sourceEmailId;
    }
}
