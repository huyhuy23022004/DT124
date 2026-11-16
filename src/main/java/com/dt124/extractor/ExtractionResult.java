package com.dt124.extractor;

import com.dt124.model.EmailFeatureRecord;
import com.dt124.model.ParsedEmail;

/**
 * Complete extraction product for one message: the ML feature record plus the richer structures
 * needed for paper replication, error analysis and reporting.
 */
public final class ExtractionResult {

    private final ParsedEmail parsedEmail;
    private final EmailFeatureRecord featureRecord;
    private final UrlAnalysis urlAnalysis;
    private final AttachmentAnalysis attachmentAnalysis;
    private final IndicatorAnalysis indicatorAnalysis;

    public ExtractionResult(ParsedEmail parsedEmail, EmailFeatureRecord featureRecord,
                            UrlAnalysis urlAnalysis, AttachmentAnalysis attachmentAnalysis,
                            IndicatorAnalysis indicatorAnalysis) {
        this.parsedEmail = parsedEmail;
        this.featureRecord = featureRecord;
        this.urlAnalysis = urlAnalysis;
        this.attachmentAnalysis = attachmentAnalysis;
        this.indicatorAnalysis = indicatorAnalysis;
    }

    public ParsedEmail getParsedEmail() {
        return parsedEmail;
    }

    public EmailFeatureRecord getFeatureRecord() {
        return featureRecord;
    }

    public UrlAnalysis getUrlAnalysis() {
        return urlAnalysis;
    }

    public AttachmentAnalysis getAttachmentAnalysis() {
        return attachmentAnalysis;
    }

    public IndicatorAnalysis getIndicatorAnalysis() {
        return indicatorAnalysis;
    }
}
