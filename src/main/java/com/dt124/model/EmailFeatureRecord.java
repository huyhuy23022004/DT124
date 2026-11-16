package com.dt124.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fully extracted feature vector for a single .eml file, spanning the four feature groups
 * (Header/Auth, Paper tyag020 URL/Domain, Safe Attachment, YARA-style Indicators) plus the
 * baseline features. Also carries non-feature metadata used for grouping / leakage control.
 */
public final class EmailFeatureRecord {

    public static final int LABEL_BENIGN = 0;
    public static final int LABEL_PHISHING = 1;

    private String id = "";
    private String sourcePath = "";
    private String sourceCorpus = "unknown";
    private int label;

    /** Grouping key (sender domain) used for group-based splits to avoid train/test leakage. */
    private String groupKey = "";
    private String contentKey = "";

    private final Map<String, Double> features = new LinkedHashMap<>();

    private AuthResult authResult = new AuthResult();
    private int attachmentCount;
    private String notes = "";

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id == null ? "" : id;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath == null ? "" : sourcePath;
    }

    public String getSourceCorpus() {
        return sourceCorpus;
    }

    public void setSourceCorpus(String sourceCorpus) {
        this.sourceCorpus = sourceCorpus == null ? "unknown" : sourceCorpus;
    }

    public int getLabel() {
        return label;
    }

    public void setLabel(int label) {
        this.label = label;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey == null ? "" : groupKey;
    }

    public String getContentKey() {
        return contentKey;
    }

    public void setContentKey(String contentKey) {
        this.contentKey = contentKey == null ? "" : contentKey;
    }

    public Map<String, Double> getFeatures() {
        return features;
    }

    public Map<String, Double> features() {
        return features;
    }

    public void put(String name, double value) {
        features.put(name, value);
    }

    public double get(String name) {
        Double value = features.get(name);
        return value == null ? 0.0d : value;
    }

    public AuthResult getAuthResult() {
        return authResult;
    }

    public void setAuthResult(AuthResult authResult) {
        this.authResult = authResult == null ? new AuthResult() : authResult;
    }

    public int getAttachmentCount() {
        return attachmentCount;
    }

    public void setAttachmentCount(int attachmentCount) {
        this.attachmentCount = attachmentCount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes;
    }
}
