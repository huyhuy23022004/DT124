package com.dt124.model;

/**
 * Result of parsing the authentication related headers of an .eml message:
 * Authentication-Results, Received-SPF and DKIM-Signature, plus sender identity fields.
 */
public final class AuthResult {

    public enum Status {
        PASS, FAIL, SOFTFAIL, NEUTRAL, NONE, MISSING
    }

    private Status spf = Status.MISSING;
    private Status dkim = Status.MISSING;
    private Status dmarc = Status.MISSING;

    private int receivedHops;
    private String fromAddress = "";
    private String returnPath = "";
    private String replyTo = "";
    private String to = "";
    private String subject = "";
    private String date = "";

    private boolean fromReturnPathMismatch;
    private boolean fromReplyToMismatch;
    private boolean hasAuthenticationResults;
    private boolean hasDkimSignature;
    private boolean hasReceivedSpf;
    private boolean replyToFreeMail;

    public Status getSpf() {
        return spf;
    }

    public void setSpf(Status spf) {
        this.spf = spf;
    }

    public Status getDkim() {
        return dkim;
    }

    public void setDkim(Status dkim) {
        this.dkim = dkim;
    }

    public Status getDmarc() {
        return dmarc;
    }

    public void setDmarc(Status dmarc) {
        this.dmarc = dmarc;
    }

    public int getReceivedHops() {
        return receivedHops;
    }

    public void setReceivedHops(int receivedHops) {
        this.receivedHops = receivedHops;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress == null ? "" : fromAddress;
    }

    public String getReturnPath() {
        return returnPath;
    }

    public void setReturnPath(String returnPath) {
        this.returnPath = returnPath == null ? "" : returnPath;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo == null ? "" : replyTo;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to == null ? "" : to;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject == null ? "" : subject;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date == null ? "" : date;
    }

    public boolean isFromReturnPathMismatch() {
        return fromReturnPathMismatch;
    }

    public void setFromReturnPathMismatch(boolean fromReturnPathMismatch) {
        this.fromReturnPathMismatch = fromReturnPathMismatch;
    }

    public boolean isFromReplyToMismatch() {
        return fromReplyToMismatch;
    }

    public void setFromReplyToMismatch(boolean fromReplyToMismatch) {
        this.fromReplyToMismatch = fromReplyToMismatch;
    }

    public boolean isHasAuthenticationResults() {
        return hasAuthenticationResults;
    }

    public void setHasAuthenticationResults(boolean hasAuthenticationResults) {
        this.hasAuthenticationResults = hasAuthenticationResults;
    }

    public boolean isHasDkimSignature() {
        return hasDkimSignature;
    }

    public void setHasDkimSignature(boolean hasDkimSignature) {
        this.hasDkimSignature = hasDkimSignature;
    }

    public boolean isHasReceivedSpf() {
        return hasReceivedSpf;
    }

    public void setHasReceivedSpf(boolean hasReceivedSpf) {
        this.hasReceivedSpf = hasReceivedSpf;
    }

    public boolean isReplyToFreeMail() {
        return replyToFreeMail;
    }

    public void setReplyToFreeMail(boolean replyToFreeMail) {
        this.replyToFreeMail = replyToFreeMail;
    }
}
