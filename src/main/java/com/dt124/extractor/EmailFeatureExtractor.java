package com.dt124.extractor;

import com.dt124.ml.FeatureSchema;
import com.dt124.model.AuthResult;
import com.dt124.model.EmailFeatureRecord;
import com.dt124.model.ParsedEmail;
import com.dt124.util.HashUtil;

import java.util.List;
import java.util.Locale;

/**
 * Orchestrates all offline extractors for a single message and produces a fully-populated
 * {@link EmailFeatureRecord}. This is the boundary where the four feature groups are assembled:
 * Header/Auth, Paper tyag020 URL/Domain, Safe Attachment and YARA-style indicators.
 */
public final class EmailFeatureExtractor {

    private static final List<String> PHISHING_KEYWORDS = List.of(
            "verify", "account", "suspended", "urgent", "click", "login", "password", "secure",
            "update", "billing", "payment", "confirm", "banking", "invoice", "delivery", "parcel",
            "refund", "winner", "prize", "gift", "crypto", "wallet", "bitcoin", "limited",
            "warning", "alert", "unauthorized", "expire", "reset", "ssn", "social security",
            "immediately", "confirm your", "sign in"
    );

    private final AuthHeaderValidator authValidator = new AuthHeaderValidator();
    private final UrlDomainAnalyzer urlAnalyzer = new UrlDomainAnalyzer();
    private final SafeAttachmentInspector attachmentInspector = new SafeAttachmentInspector();
    private final SafeIndicatorRuleEngine ruleEngine;
    private final String sourceCorpus;
    private final int label;

    public EmailFeatureExtractor(SafeIndicatorRuleEngine ruleEngine, String sourceCorpus, int label) {
        this.ruleEngine = ruleEngine;
        this.sourceCorpus = sourceCorpus;
        this.label = label;
    }

    public ExtractionResult extract(ParsedEmail email) {
        AuthResult auth = authValidator.validate(email);
        UrlAnalysis urlAnalysis = urlAnalyzer.analyze(email);
        AttachmentAnalysis attachmentAnalysis = attachmentInspector.inspect(email.getAttachments());
        IndicatorAnalysis indicators = ruleEngine.scan(buildScanText(email, attachmentAnalysis));

        EmailFeatureRecord record = new EmailFeatureRecord();
        record.setSourceCorpus(sourceCorpus);
        record.setLabel(label);
        record.setSourcePath(email.getSourcePath());
        record.setAuthResult(auth);
        record.setAttachmentCount(email.getAttachments().size());
        record.setContentKey(HashUtil.contentKey(email.getSubject(), email.combinedBody()));

        String groupKey = AuthHeaderValidator.extractDomain(email.getFrom());
        if (groupKey.isEmpty()) {
            groupKey = AuthHeaderValidator.extractDomain(email.getReturnPath());
        }
        if (groupKey.isEmpty()) {
            groupKey = urlAnalysis.getPrimaryHost();
        }
        record.setGroupKey(groupKey);
        record.setId(email.getMessageId().isEmpty()
                ? record.getContentKey().substring(0, 16)
                : email.getMessageId());

        putHeaderFeatures(record, auth, email);
        putBaselineFeatures(record, email);
        putUrlFeatures(record, urlAnalysis);
        putAttachmentFeatures(record, attachmentAnalysis);
        putIndicatorFeatures(record, indicators);

        // Ensure every schema feature exists (zeros for anything not explicitly set).
        for (String feature : FeatureSchema.ALL) {
            record.getFeatures().putIfAbsent(feature, 0.0d);
        }

        return new ExtractionResult(email, record, urlAnalysis, attachmentAnalysis, indicators);
    }

    private void putHeaderFeatures(EmailFeatureRecord record, AuthResult auth, ParsedEmail email) {
        record.put("received_hops", auth.getReceivedHops());
        record.put("from_return_path_mismatch", auth.isFromReturnPathMismatch() ? 1.0d : 0.0d);
        record.put("from_reply_to_mismatch", auth.isFromReplyToMismatch() ? 1.0d : 0.0d);
        record.put("spf_missing", auth.getSpf() == AuthResult.Status.MISSING ? 1.0d : 0.0d);
        record.put("spf_fail",
                isFail(auth.getSpf()) ? 1.0d : 0.0d);
        record.put("dkim_missing", auth.getDkim() == AuthResult.Status.MISSING ? 1.0d : 0.0d);
        record.put("dkim_fail", isFail(auth.getDkim()) ? 1.0d : 0.0d);
        record.put("dmarc_missing", auth.getDmarc() == AuthResult.Status.MISSING ? 1.0d : 0.0d);
        record.put("dmarc_fail", isFail(auth.getDmarc()) ? 1.0d : 0.0d);
        record.put("reply_to_free_mail", auth.isReplyToFreeMail() ? 1.0d : 0.0d);
    }

    private void putBaselineFeatures(EmailFeatureRecord record, ParsedEmail email) {
        String haystack = (email.getSubject() + "\n" + email.combinedBody()).toLowerCase(Locale.ROOT);
        int keywordCount = 0;
        for (String keyword : PHISHING_KEYWORDS) {
            keywordCount += countOccurrences(haystack, keyword);
        }
        record.put("kw_phishing_count", keywordCount);
        record.put("subject_len", email.getSubject().length());
    }

    private void putUrlFeatures(EmailFeatureRecord record, UrlAnalysis analysis) {
        record.put("url_count", analysis.getUrlCount());
        record.put("url_ip_count", analysis.getIpUrlCount());
        record.put("url_free_hosting_abuse", analysis.isFreeHostingAbuse() ? 1.0d : 0.0d);
        record.put("url_suspicious_tld", analysis.isSuspiciousTld() ? 1.0d : 0.0d);
        record.put("url_brand_hyphen", analysis.isBrandHyphen() ? 1.0d : 0.0d);
        record.put("url_max_subdomains", analysis.getMaxSubdomains());
        record.put("domain_entropy_max", analysis.getMaxDomainEntropy());
        record.put("html_anchor_mismatch", analysis.isHtmlAnchorMismatch() ? 1.0d : 0.0d);
    }

    private void putAttachmentFeatures(EmailFeatureRecord record, AttachmentAnalysis analysis) {
        record.put("att_count", analysis.getCount());
        record.put("att_dangerous_ext", analysis.getDangerousExtensionCount());
        record.put("att_double_ext", analysis.getDoubleExtensionCount());
        record.put("att_magic_mismatch", analysis.getMagicMismatchCount());
        record.put("att_max_entropy", analysis.getMaxEntropy());
    }

    private void putIndicatorFeatures(EmailFeatureRecord record, IndicatorAnalysis indicators) {
        record.put("yara_eval_obfuscation", indicators.get("eval_obfuscation"));
        record.put("yara_base64_blob", indicators.get("base64_blob"));
        record.put("yara_hidden_login_form", indicators.get("hidden_login_form"));
        record.put("yara_onload_js", indicators.get("onload_js"));
        record.put("yara_total_hits", indicators.getTotalHits());
    }

    private String buildScanText(ParsedEmail email, AttachmentAnalysis attachmentAnalysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("Subject: ").append(email.getSubject()).append('\n');
        sb.append("From: ").append(email.getFrom()).append('\n');
        sb.append("Return-Path: ").append(email.getReturnPath()).append('\n');
        sb.append("Reply-To: ").append(email.getReplyTo()).append('\n');
        sb.append("Authentication-Results: ").append(email.getAuthResults()).append('\n');
        List<String> headerLines = email.getRawHeaders().get("__lines__");
        if (headerLines != null) {
            for (String line : headerLines) {
                sb.append(line).append('\n');
            }
        }
        sb.append("\n--- TEXT BODY ---\n").append(email.getTextBody()).append('\n');
        sb.append("\n--- HTML BODY ---\n").append(email.getHtmlBody()).append('\n');
        if (attachmentAnalysis != null) {
            sb.append("\n--- ATTACHMENTS ---\n");
            for (var meta : attachmentAnalysis.getAttachments()) {
                sb.append(meta.getFileName()).append(' ').append(meta.getDetectedType()).append('\n');
            }
        }
        return sb.toString();
    }

    private static boolean isFail(AuthResult.Status status) {
        return status == AuthResult.Status.FAIL || status == AuthResult.Status.SOFTFAIL;
    }

    private static int countOccurrences(String haystack, String needle) {
        if (haystack.isEmpty() || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
