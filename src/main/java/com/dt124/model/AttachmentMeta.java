package com.dt124.model;

/**
 * Safe attachment metadata. The attachment content is only ever inspected in RAM and never written
 * to disk or executed (zero-execution requirement).
 */
public final class AttachmentMeta {

    private String fileName = "";
    private String contentType = "";
    private long declaredSize;
    private int inspectedBytes;
    private String extension = "";
    private boolean dangerousExtension;
    private boolean doubleExtension;
    private boolean magicMismatch;
    private boolean pathTraversal;
    private double entropy;
    private String sha256 = "";
    private String detectedType = "unknown";

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName == null ? "" : fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType == null ? "" : contentType;
    }

    public long getDeclaredSize() {
        return declaredSize;
    }

    public void setDeclaredSize(long declaredSize) {
        this.declaredSize = declaredSize;
    }

    public int getInspectedBytes() {
        return inspectedBytes;
    }

    public void setInspectedBytes(int inspectedBytes) {
        this.inspectedBytes = inspectedBytes;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension == null ? "" : extension;
    }

    public boolean isDangerousExtension() {
        return dangerousExtension;
    }

    public void setDangerousExtension(boolean dangerousExtension) {
        this.dangerousExtension = dangerousExtension;
    }

    public boolean isDoubleExtension() {
        return doubleExtension;
    }

    public void setDoubleExtension(boolean doubleExtension) {
        this.doubleExtension = doubleExtension;
    }

    public boolean isMagicMismatch() {
        return magicMismatch;
    }

    public void setMagicMismatch(boolean magicMismatch) {
        this.magicMismatch = magicMismatch;
    }

    public boolean isPathTraversal() {
        return pathTraversal;
    }

    public void setPathTraversal(boolean pathTraversal) {
        this.pathTraversal = pathTraversal;
    }

    public double getEntropy() {
        return entropy;
    }

    public void setEntropy(double entropy) {
        this.entropy = entropy;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256 == null ? "" : sha256;
    }

    public String getDetectedType() {
        return detectedType;
    }

    public void setDetectedType(String detectedType) {
        this.detectedType = detectedType == null ? "unknown" : detectedType;
    }

    @Override
    public String toString() {
        return "AttachmentMeta{name='" + fileName + "', ext='" + extension + "', dangerous="
                + dangerousExtension + ", doubleExt=" + doubleExtension + ", magicMismatch="
                + magicMismatch + ", entropy=" + String.format(java.util.Locale.ROOT, "%.2f", entropy)
                + ", detected='" + detectedType + "'}";
    }
}
