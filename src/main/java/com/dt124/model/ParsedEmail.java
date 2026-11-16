package com.dt124.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Intermediate representation of an .eml file produced by {@code OfflineEmlParser}.
 * Contains everything required by the extractors without touching disk or the network.
 */
public final class ParsedEmail {

    private String sourcePath = "";
    private boolean parsedOk;
    private String parseError = "";

    private String messageId = "";
    private String subject = "";
    private String from = "";
    private String to = "";
    private String cc = "";
    private String replyTo = "";
    private String returnPath = "";
    private String date = "";

    private String authResults = "";
    private String receivedSpf = "";
    private String dkimSignature = "";
    private final List<String> receivedHeaders = new ArrayList<>();
    private final Map<String, List<String>> rawHeaders = new LinkedHashMap<>();

    private String textBody = "";
    private String htmlBody = "";
    private final List<RawAttachment> attachments = new ArrayList<>();

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath == null ? "" : sourcePath;
    }

    public boolean isParsedOk() {
        return parsedOk;
    }

    public void setParsedOk(boolean parsedOk) {
        this.parsedOk = parsedOk;
    }

    public String getParseError() {
        return parseError;
    }

    public void setParseError(String parseError) {
        this.parseError = parseError == null ? "" : parseError;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId == null ? "" : messageId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject == null ? "" : subject;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from == null ? "" : from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to == null ? "" : to;
    }

    public String getCc() {
        return cc;
    }

    public void setCc(String cc) {
        this.cc = cc == null ? "" : cc;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo == null ? "" : replyTo;
    }

    public String getReturnPath() {
        return returnPath;
    }

    public void setReturnPath(String returnPath) {
        this.returnPath = returnPath == null ? "" : returnPath;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date == null ? "" : date;
    }

    public String getAuthResults() {
        return authResults;
    }

    public void setAuthResults(String authResults) {
        this.authResults = authResults == null ? "" : authResults;
    }

    public String getReceivedSpf() {
        return receivedSpf;
    }

    public void setReceivedSpf(String receivedSpf) {
        this.receivedSpf = receivedSpf == null ? "" : receivedSpf;
    }

    public String getDkimSignature() {
        return dkimSignature;
    }

    public void setDkimSignature(String dkimSignature) {
        this.dkimSignature = dkimSignature == null ? "" : dkimSignature;
    }

    public List<String> getReceivedHeaders() {
        return receivedHeaders;
    }

    public Map<String, List<String>> getRawHeaders() {
        return rawHeaders;
    }

    public String getTextBody() {
        return textBody;
    }

    public void setTextBody(String textBody) {
        this.textBody = textBody == null ? "" : textBody;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public void setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody == null ? "" : htmlBody;
    }

    public List<RawAttachment> getAttachments() {
        return attachments;
    }

    /**
     * Convenience: combined text used for keyword/entropy features.
     */
    public String combinedBody() {
        return (textBody == null ? "" : textBody) + "\n" + (htmlBody == null ? "" : htmlBody);
    }
}
