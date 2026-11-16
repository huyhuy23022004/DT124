package com.dt124.ml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Central definition of the feature schema and feature groups. Keeping the schema in one place
 * guarantees that the parser, the ARFF builder and every ML experiment agree on names/order.
 */
public final class FeatureSchema {

    public static final String CLASS_ATTRIBUTE = "class";
    public static final String POSITIVE_CLASS = "phishing";
    public static final String NEGATIVE_CLASS = "benign";

    /** Minimal features used by the Baseline model. */
    public static final List<String> BASELINE;

    /** Header / authentication / delivery-context features (paper limitation 2). */
    public static final List<String> HEADER_AUTH;

    /** URL / domain features reproducing the tyag020 paper (RQ1-RQ2, limitation 1 & 3). */
    public static final List<String> PAPER_URL;

    /** Safe attachment metadata features (limitation 5). */
    public static final List<String> ATTACHMENT;

    /** YARA-style safe-indicator rule hits. */
    public static final List<String> YARA;

    /** Full ordered, de-duplicated feature list used to build Weka instances. */
    public static final List<String> ALL;

    static {
        BASELINE = List.of(
                "kw_phishing_count",
                "url_count",
                "subject_len"
        );

        HEADER_AUTH = List.of(
                "received_hops",
                "from_return_path_mismatch",
                "from_reply_to_mismatch",
                "spf_missing",
                "spf_fail",
                "dkim_missing",
                "dkim_fail",
                "dmarc_missing",
                "dmarc_fail",
                "reply_to_free_mail"
        );

        PAPER_URL = List.of(
                "url_count",
                "url_ip_count",
                "url_free_hosting_abuse",
                "url_suspicious_tld",
                "url_brand_hyphen",
                "url_max_subdomains",
                "domain_entropy_max",
                "html_anchor_mismatch"
        );

        ATTACHMENT = List.of(
                "att_count",
                "att_dangerous_ext",
                "att_double_ext",
                "att_magic_mismatch",
                "att_max_entropy"
        );

        YARA = List.of(
                "yara_eval_obfuscation",
                "yara_base64_blob",
                "yara_hidden_login_form",
                "yara_onload_js",
                "yara_total_hits"
        );

        Set<String> ordered = new LinkedHashSet<>();
        ordered.addAll(BASELINE);
        ordered.addAll(HEADER_AUTH);
        ordered.addAll(PAPER_URL);
        ordered.addAll(ATTACHMENT);
        ordered.addAll(YARA);
        ALL = Collections.unmodifiableList(new ArrayList<>(ordered));
    }

    private FeatureSchema() {
    }

    /**
     * Returns the full feature list minus the named group. Used by the ablation study.
     */
    public static List<String> allExcept(List<String> group) {
        List<String> result = new ArrayList<>(ALL);
        result.removeAll(group);
        return result;
    }
}
