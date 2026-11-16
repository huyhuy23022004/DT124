package com.dt124.dataset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Deterministically generates a labelled, realistic .eml corpus so the full pipeline and ML
 * experiments can run immediately without downloading/extracting the ~1.7 GB Enron archive.
 *
 * <p>The corpus is deliberately non-trivial: benign messages include keyword-heavy "marketing"
 * mail and real-brand senders (hard negatives), while phishing messages include attachment-only
 * and URL-only lures with almost no phishing keywords (hard positives). This ensures the minimal
 * Baseline model makes real errors while the full Proposed model can exploit header, URL,
 * attachment and rule signals to do better.</p>
 */
public final class SyntheticEmailGenerator {

    private final Random random;

    private static final String[] BENIGN_SENDERS = {
            "kay.mann@enron.com", "jeff.skilling@enron.com", "tim.belden@enron.com",
            "sally.beck@enron.com", "richard.shapiro@enron.com", "michelle.lokay@enron.com",
            "john.lavorato@enron.com", "greg.whalley@enron.com", "mark.taylor@enron.com",
            "david.delainey@enron.com"
    };

    private static final String[] BENIGN_SIMPLE_SUBJECTS = {
            "Meeting notes for the west power desk",
            "Re: gas schedule update",
            "Weekly trading summary",
            "Lunch on Friday?",
            "Draft contract for review",
            "Power outage maintenance window",
            "Quarterly compliance reminder",
            "Conference call dial-in details",
            "Updated holiday calendar",
            "Team offsite planning"
    };

    private static final String[] BENIGN_MARKETING_SUBJECTS = {
            "Action required: verify your account and update billing",
            "Confirm your payment method to avoid suspension",
            "Security notice: please sign in and confirm your identity",
            "Your subscription is about to expire - update payment",
            "Important: verify your account details today",
            "Please confirm your billing information immediately"
    };

    private static final String[] PHISHING_SUBJECTS = {
            "Your account has been suspended - immediate action required",
            "Verify your account within 24 hours",
            "Unusual sign-in attempt detected",
            "Confirm your billing information now",
            "Final notice: update your payment method",
            "Your parcel could not be delivered - click here",
            "Security alert: unauthorized login to your account",
            "Action required: confirm your identity",
            "Reset your password immediately",
            "Suspicious activity on your wallet"
    };

    private static final String[] PHISHING_HOSTS = {
            "pay-ap.top", "apple-verify.top", "secure-dhl.xyz", "usps-tracking.icu",
            "microsoft-support.vip", "netflix-billing.shop", "amazon-security.click",
            "chase-alerts.online", "hsbc-secure.xyz", "coinbase-wallet.top",
            "wells-fargo-alert.top", "citibank-verify.xyz"
    };

    private static final String[] FREE_HOSTING_HOSTS = {
            "apple-login.web.app", "dhl-verify.pages.dev", "firebase-auth.firebaseapp.com",
            "secure-paypal.vercel.app", "microsoft-sso.workers.dev", "netflix-account.pages.dev"
    };

    private static final String[] DOCUMENTATION_IPS = {
            "203.0.113.10", "198.51.100.24", "192.0.2.44"
    };

    private static final String[] REAL_BRAND_DOMAINS = {
            "paypal.com", "microsoft.com", "amazon.com", "netflix.com"
    };

    public SyntheticEmailGenerator(long seed) {
        this.random = new Random(seed);
    }

    public GenerationReport generate(Path benignDir, Path phishingDir, int benignCount, int phishingCount)
            throws IOException {
        Files.createDirectories(benignDir);
        Files.createDirectories(phishingDir);

        GenerationReport report = new GenerationReport();
        for (int i = 0; i < benignCount; i++) {
            Path file = benignDir.resolve(String.format(Locale.ROOT, "benign_%04d.eml", i));
            Files.writeString(file, buildBenign(i), StandardCharsets.UTF_8);
            report.getBenignFiles().add(file);
        }
        for (int i = 0; i < phishingCount; i++) {
            Path file = phishingDir.resolve(String.format(Locale.ROOT, "phishing_%04d.eml", i));
            Files.writeString(file, buildPhishing(i), StandardCharsets.UTF_8);
            report.getPhishingFiles().add(file);
        }

        // Deliberately inject a few exact duplicates so the deduplication stage is demonstrated.
        for (int i = 0; i < Math.min(5, benignCount); i++) {
            Path original = benignDir.resolve(String.format(Locale.ROOT, "benign_%04d.eml", i));
            Path duplicate = benignDir.resolve(String.format(Locale.ROOT, "benign_dup_%04d.eml", i));
            Files.copy(original, duplicate, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            report.getBenignFiles().add(duplicate);
        }
        return report;
    }

    // ------------------------------------------------------------------------------------------------
    // Benign
    // ------------------------------------------------------------------------------------------------

    private String buildBenign(int index) {
        return switch (index % 4) {
            case 0, 1 -> buildSimpleBenign(index);
            case 2 -> buildNewsletterBenign(index);
            default -> buildMarketingBenign(index);
        };
    }

    private String buildSimpleBenign(int index) {
        String sender = BENIGN_SENDERS[index % BENIGN_SENDERS.length];
        String subject = BENIGN_SIMPLE_SUBJECTS[(index / 2) % BENIGN_SIMPLE_SUBJECTS.length]
                + " [ref " + ref(index, 'S') + "]";
        String body = "Hi,\n\nPlease review the attached schedule before Friday. "
                + "No action needed beyond that.\nReference: " + ref(index, 'B') + "\n\nThanks,\nColleague";
        return buildBenignMessage(index, sender, subject, body, false, false, false);
    }

    private String buildNewsletterBenign(int index) {
        String sender = "newsletter@" + (index % 2 == 0 ? "bloomberg.com" : "reuters.com");
        String subject = "Market briefing and weekly highlights [ref " + ref(index, 'N') + "]";
        String urls = "https://www." + (index % 2 == 0 ? "bloomberg.com" : "reuters.com")
                + "/markets\nhttps://www." + (index % 2 == 0 ? "bloomberg.com" : "reuters.com")
                + "/energy\nhttps://careers." + (index % 2 == 0 ? "bloomberg.com" : "reuters.com")
                + "/openings";
        String body = "Hello,\n\nHere is your market briefing.\nRead more:\n" + urls
                + "\n\nReference: " + ref(index, 'N');
        return buildBenignMessage(index, sender, subject, body, false, true, false);
    }

    private String buildMarketingBenign(int index) {
        String sender = "service@" + REAL_BRAND_DOMAINS[index % REAL_BRAND_DOMAINS.length];
        String subject = BENIGN_MARKETING_SUBJECTS[index % BENIGN_MARKETING_SUBJECTS.length]
                + " [ref " + ref(index, 'M') + "]";
        // Deliberately keyword-heavy but legitimate: this is a hard negative for the baseline.
        String body = "Dear customer,\n\nPlease verify your account and confirm your billing information "
                + "to update the payment method. Click here to sign in and reset your password if needed. "
                + "This is a secure notice about your account and payment details.\nReference: " + ref(index, 'M');
        return buildBenignMessage(index, sender, subject, body, false, true, true);
    }

    private String buildBenignMessage(int index, String sender, String subject, String body,
                                      boolean forceAuthGap, boolean includeHtml, boolean includeSafeAttachment) {
        String recipient = "employee" + (index % 40) + "@enron.com";
        String messageId = "<benign-" + index + "-" + UUID.randomUUID() + "@" + domainOf(sender) + ">";
        String boundary = "bnd-b-" + index + "-" + Integer.toHexString(random.nextInt());
        int hops = 1 + random.nextInt(3);

        String html = "<html><body><p>" + escapeHtml(body).replace("\n", "<br/>") + "</p>"
                + "<p><a href=\"https://www." + domainOf(sender) + "/portal\">Open portal</a></p></body></html>";

        StringBuilder sb = new StringBuilder();
        appendCommonHeaders(sb, sender, recipient, subject, messageId, hops);
        sb.append("Return-Path: <").append(sender).append(">\r\n");
        sb.append("Reply-To: ").append(sender).append("\r\n");
        String d = domainOf(sender);
        if (forceAuthGap) {
            sb.append("Authentication-Results: mx.example.com; spf=softfail smtp.mailfrom=").append(d)
                    .append("; dkim=none; dmarc=none\r\n");
            sb.append("Received-SPF: softfail (mx.example.com: transitioning domain of ").append(d).append(")\r\n");
        } else {
            sb.append("Authentication-Results: mx.example.com; spf=pass smtp.mailfrom=").append(d)
                    .append("; dkim=pass header.d=").append(d).append("; dmarc=pass header.from=").append(d)
                    .append("\r\n");
            sb.append("Received-SPF: pass (mx.example.com: domain of ").append(sender)
                    .append(" designates 192.0.2.").append(10 + (index % 50)).append(" as permitted sender)\r\n");
            sb.append("DKIM-Signature: v=1; a=rsa-sha256; d=").append(d)
                    .append("; s=sel; h=from:to:subject;\r\n");
        }
        sb.append("MIME-Version: 1.0\r\n");

        if (includeSafeAttachment) {
            sb.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n");
            appendTextPart(sb, boundary, body);
            if (includeHtml) {
                appendHtmlPart(sb, boundary, html);
            }
            appendAttachment(sb, boundary, "statement.pdf", "application/pdf", pdfPayload(index));
            sb.append("--").append(boundary).append("--\r\n");
        } else if (includeHtml) {
            sb.append("Content-Type: multipart/alternative; boundary=\"").append(boundary).append("\"\r\n\r\n");
            appendTextPart(sb, boundary, body);
            appendHtmlPart(sb, boundary, html);
            sb.append("--").append(boundary).append("--\r\n");
        } else {
            sb.append("Content-Type: text/plain; charset=\"UTF-8\"\r\n\r\n");
            sb.append(body).append("\r\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------------
    // Phishing
    // ------------------------------------------------------------------------------------------------

    private String buildPhishing(int index) {
        return switch (index % 5) {
            case 0 -> buildClassicPhishing(index);
            case 1 -> buildAttachmentOnlyPhishing(index);
            case 2 -> buildUrlOnlyPhishing(index);
            case 3 -> buildFreeHostingPhishing(index);
            default -> buildAuthOnlyPhishing(index);
        };
    }

    private String buildClassicPhishing(int index) {
        String host = PHISHING_HOSTS[index % PHISHING_HOSTS.length];
        String subject = PHISHING_SUBJECTS[index % PHISHING_SUBJECTS.length];
        String url = "http://" + host + "/login";
        String body = "Dear customer,\n\nWe detected unusual activity on your account. "
                + "You must verify your account immediately or it will be suspended. "
                + "Click here to sign in and confirm your identity.\n" + url + "\n\nReference: " + ref(index, 'P');
        return buildPhishingMessage(index, host, subject, body, false, false, true);
    }

    private String buildAttachmentOnlyPhishing(int index) {
        String host = PHISHING_HOSTS[index % PHISHING_HOSTS.length];
        // Keyword-free, URL-free, link-free and auth-passing: only the safe-attachment signals
        // (double extension / magic bytes) reveal it. Subject/body length mirrors benign mail.
        String subject = BENIGN_SIMPLE_SUBJECTS[(index / 2) % BENIGN_SIMPLE_SUBJECTS.length]
                + " [ref " + ref(index, 'A') + "]";
        String body = "Hi,\n\nPlease review the attached schedule before Friday. "
                + "No action needed beyond that.\nReference: " + ref(index, 'A') + "\n\nThanks,\nColleague";
        return buildPhishingMessage(index, host, subject, body, true, true, false);
    }

    private String buildUrlOnlyPhishing(int index) {
        String host = FREE_HOSTING_HOSTS[index % FREE_HOSTING_HOSTS.length];
        // Auth-passing with a clean-looking link on a brand-lookalike subdomain: only the URL /
        // domain features give it away.
        String subject = "Monthly statement available [ref " + ref(index, 'U') + "]";
        String url = "https://" + host + "/statement/" + ref(index, 'U');
        String body = "Hello,\n\nYour monthly statement is ready. Open the link below.\n"
                + url + "\n\nReference: " + ref(index, 'U');
        return buildPhishingMessage(index, host, subject, body, true, false, true);
    }

    private String buildFreeHostingPhishing(int index) {
        String host = FREE_HOSTING_HOSTS[index % FREE_HOSTING_HOSTS.length];
        String brand = brandFor(host);
        String subject = "Access notice from " + brand + " [ref " + ref(index, 'F') + "]";
        String url = "https://login.secure." + host + "/auth?t=" + ref(index, 'F');
        String body = "Hello,\n\nA new access notice is available for your profile.\n"
                + "Review it here: " + url + "\n\nReference: " + ref(index, 'F');
        return buildPhishingMessage(index, host, subject, body, false, false, true);
    }

    private String buildAuthOnlyPhishing(int index) {
        String host = PHISHING_HOSTS[index % PHISHING_HOSTS.length];
        // Stealthy lure: no keywords, no URL, no attachment, no HTML links and benign-looking
        // subject/body. Only failing authentication (SPF/DKIM/DMARC), return-path mismatch and a
        // free-mail Reply-To reveal it.
        String subject = BENIGN_SIMPLE_SUBJECTS[(index / 2) % BENIGN_SIMPLE_SUBJECTS.length]
                + " [ref " + ref(index, 'H') + "]";
        String body = "Hi,\n\nPlease review the schedule before Friday. "
                + "No action needed beyond that.\nReference: " + ref(index, 'H') + "\n\nThanks,\nColleague";
        return buildPhishingMessage(index, host, subject, body, false, false, false);
    }

    private String buildPhishingMessage(int index, String host, String subject, String body,
                                        boolean authPass, boolean attachmentLure, boolean withLinks) {
        String sender = "notice@" + host;
        String recipient = "victim" + (index % 60) + "@example.com";
        String messageId = "<phish-" + index + "-" + UUID.randomUUID() + "@" + host + ">";
        String boundary = "bnd-p-" + index + "-" + Integer.toHexString(random.nextInt());
        int hops = 3 + random.nextInt(6);

        String html = null;
        if (withLinks) {
            String anchorHref = "http://" + host + "/login";
            String anchorText = "https://www." + brandlessHost(host) + "/signin";
            html = "<html><body onload=\"track()\"><p>" + escapeHtml(body).replace("\n", "<br/>")
                    + "</p><p><a href=\"" + anchorHref + "\">" + anchorText + "</a></p>"
                    + "<form action=\"" + anchorHref + "\" method=\"post\">"
                    + "<input type=\"text\" name=\"user\"/>"
                    + "<input type=\"password\" name=\"pass\" style=\"display:none\"/>"
                    + "<input type=\"submit\" value=\"Continue\"/></form>"
                    + "<script>var s=\"review the notice\";eval(atob('" + base64Blob() + "'));</script>"
                    + "</body></html>";
        }

        StringBuilder sb = new StringBuilder();
        appendCommonHeaders(sb, sender, recipient, subject, messageId, hops);
        String d = domainOf(sender);
        if (authPass) {
            sb.append("Return-Path: <").append(sender).append(">\r\n");
            sb.append("Reply-To: ").append(sender).append("\r\n");
            sb.append("Authentication-Results: mx.example.com; spf=pass smtp.mailfrom=").append(d)
                    .append("; dkim=pass header.d=").append(d).append("; dmarc=pass header.from=").append(d)
                    .append("\r\n");
            sb.append("Received-SPF: pass (mx.example.com: domain of ").append(sender)
                    .append(" designates 192.0.2.").append(20 + (index % 40)).append(" as permitted sender)\r\n");
            sb.append("DKIM-Signature: v=1; a=rsa-sha256; d=").append(d)
                    .append("; s=default; h=from:to:subject;\r\n");
        } else {
            sb.append("Return-Path: <bounce@").append(differentDomain(host)).append(">\r\n");
            sb.append("Reply-To: recovery").append(index % 10).append("@gmail.com\r\n");
            sb.append("Authentication-Results: mx.example.com; spf=fail smtp.mailfrom=")
                    .append(differentDomain(host)).append("; dkim=fail header.d=").append(d)
                    .append("; dmarc=fail header.from=").append(d).append("\r\n");
            sb.append("Received-SPF: fail (mx.example.com: domain of ").append(sender)
                    .append(" does not designate ").append(DOCUMENTATION_IPS[index % DOCUMENTATION_IPS.length])
                    .append(" as permitted sender)\r\n");
            sb.append("DKIM-Signature: v=1; a=rsa-sha256; d=").append(d)
                    .append("; s=default; h=from:to:subject;\r\n");
        }
        sb.append("MIME-Version: 1.0\r\n");

        if (attachmentLure) {
            sb.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n");
            appendTextPart(sb, boundary, body);
            if (html != null) {
                appendHtmlPart(sb, boundary, html);
            }
            boolean doubleExtension = index % 2 == 0;
            if (doubleExtension) {
                appendAttachment(sb, boundary, "invoice.pdf.exe", "application/octet-stream", pePayload(index));
            } else {
                appendAttachment(sb, boundary, "statement.pdf", "application/pdf", pePayload(index));
            }
            sb.append("--").append(boundary).append("--\r\n");
        } else if (html != null) {
            sb.append("Content-Type: multipart/alternative; boundary=\"").append(boundary).append("\"\r\n\r\n");
            appendTextPart(sb, boundary, body);
            appendHtmlPart(sb, boundary, html);
            sb.append("--").append(boundary).append("--\r\n");
        } else {
            sb.append("Content-Type: text/plain; charset=\"UTF-8\"\r\n\r\n");
            sb.append(body).append("\r\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------------------------------------

    private void appendCommonHeaders(StringBuilder sb, String from, String to, String subject,
                                     String messageId, int hops) {
        sb.append("From: ").append(from).append("\r\n");
        sb.append("To: ").append(to).append("\r\n");
        sb.append("Subject: ").append(subject).append("\r\n");
        sb.append("Date: ").append(randomDate()).append("\r\n");
        sb.append("Message-ID: ").append(messageId).append("\r\n");
        for (int i = 0; i < hops; i++) {
            sb.append("Received: from relay").append(i).append(" (relay").append(i)
                    .append(".example.net [192.0.2.").append(100 + i).append("]) by mx")
                    .append(i).append(".example.com; ").append(randomDate()).append("\r\n");
        }
    }

    private void appendTextPart(StringBuilder sb, String boundary, String body) {
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Type: text/plain; charset=\"UTF-8\"\r\n\r\n");
        sb.append(body).append("\r\n");
    }

    private void appendHtmlPart(StringBuilder sb, String boundary, String html) {
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Type: text/html; charset=\"UTF-8\"\r\n\r\n");
        sb.append(html).append("\r\n");
    }

    private void appendAttachment(StringBuilder sb, String boundary, String fileName,
                                  String contentType, byte[] payload) {
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Type: ").append(contentType).append("; name=\"").append(fileName).append("\"\r\n");
        sb.append("Content-Transfer-Encoding: base64\r\n");
        sb.append("Content-Disposition: attachment; filename=\"").append(fileName).append("\"\r\n\r\n");
        sb.append(wrapBase64(payload)).append("\r\n");
    }

    private String randomDate() {
        int day = 1 + random.nextInt(28);
        int hour = random.nextInt(24);
        int minute = random.nextInt(60);
        return String.format(Locale.ROOT, "Tue, %02d Mar 2002 %02d:%02d:00 -0500", day, hour, minute);
    }

    private static String ref(int index, char category) {
        return String.format(Locale.ROOT, "%c%06X", category, (index * 2654435761L) & 0xFFFFFF);
    }

    private static String domainOf(String address) {
        int at = address.indexOf('@');
        return at >= 0 ? address.substring(at + 1) : "example.com";
    }

    private static String brandFor(String host) {
        if (host.contains("apple")) {
            return "Apple";
        }
        if (host.contains("dhl")) {
            return "DHL";
        }
        if (host.contains("paypal")) {
            return "PayPal";
        }
        if (host.contains("microsoft")) {
            return "Microsoft";
        }
        if (host.contains("netflix")) {
            return "Netflix";
        }
        if (host.contains("amazon")) {
            return "Amazon";
        }
        return "Support";
    }

    private static String brandlessHost(String host) {
        if (host.contains("pay")) {
            return "paypal.com";
        }
        if (host.contains("apple")) {
            return "apple.com";
        }
        if (host.contains("dhl")) {
            return "dhl.com";
        }
        if (host.contains("usps")) {
            return "usps.com";
        }
        if (host.contains("microsoft")) {
            return "microsoft.com";
        }
        if (host.contains("netflix")) {
            return "netflix.com";
        }
        if (host.contains("chase")) {
            return "chase.com";
        }
        if (host.contains("hsbc")) {
            return "hsbc.com";
        }
        if (host.contains("coinbase")) {
            return "coinbase.com";
        }
        if (host.contains("amazon")) {
            return "amazon.com";
        }
        if (host.contains("wells")) {
            return "wellsfargo.com";
        }
        if (host.contains("citibank")) {
            return "citibank.com";
        }
        return "example.com";
    }

    private static String differentDomain(String host) {
        return "mail-" + Integer.toHexString(host.hashCode()) + ".top";
    }

    private static byte[] pePayload(int index) {
        byte[] data = new byte[640];
        data[0] = 'M';
        data[1] = 'Z';
        data[2] = (byte) 0x90;
        data[3] = 0x00;
        Random r = new Random(index * 7919L);
        for (int i = 4; i < data.length; i++) {
            data[i] = (byte) r.nextInt(256);
        }
        return data;
    }

    private static byte[] pdfPayload(int index) {
        byte[] header = "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n".getBytes(StandardCharsets.US_ASCII);
        byte[] data = new byte[384];
        System.arraycopy(header, 0, data, 0, header.length);
        Random r = new Random(index * 104729L);
        for (int i = header.length; i < data.length; i++) {
            data[i] = (byte) r.nextInt(256);
        }
        return data;
    }

    private static String base64Blob() {
        byte[] data = new byte[160];
        new Random(1234).nextBytes(data);
        return Base64.getEncoder().encodeToString(data);
    }

    private static String wrapBase64(byte[] data) {
        String encoded = Base64.getEncoder().encodeToString(data);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < encoded.length(); i += 76) {
            sb.append(encoded, i, Math.min(i + 76, encoded.length())).append("\r\n");
        }
        return sb.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static final class GenerationReport {
        private final List<Path> benignFiles = new java.util.ArrayList<>();
        private final List<Path> phishingFiles = new java.util.ArrayList<>();

        public List<Path> getBenignFiles() {
            return benignFiles;
        }

        public List<Path> getPhishingFiles() {
            return phishingFiles;
        }

        public int getTotal() {
            return benignFiles.size() + phishingFiles.size();
        }
    }
}
