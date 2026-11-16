package com.dt124.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts personally identifiable information (PII) from strings before they are logged or written
 * to artifacts, as required by the ethics/safety constraints of the project.
 */
public final class LogSanitizer {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final Pattern URL_PATTERN =
            Pattern.compile("(?i)\\bhttps?://[^\\s\"'<>]+");
    private static final Pattern IPV4_PATTERN =
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    private LogSanitizer() {
    }

    /**
     * Masks local parts of emails, full URLs and IP addresses. Keeps the TLD / domain shape so the
     * sanitized text remains analytically useful without exposing identities.
     */
    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = EMAIL_PATTERN.matcher(input).replaceAll(match -> {
            String value = match.group();
            int at = value.indexOf('@');
            String domain = value.substring(at + 1);
            return "<redacted>@" + domain;
        });
        Matcher url = URL_PATTERN.matcher(result);
        result = url.replaceAll("<redacted-url>");
        result = IPV4_PATTERN.matcher(result).replaceAll("<redacted-ip>");
        return result;
    }

    /**
     * Masks the local part of a single email address, keeping the domain visible.
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "<redacted>";
        }
        return "<redacted>" + email.substring(at);
    }
}
