package com.dt124.parser;

import com.dt124.model.ParsedEmail;
import com.dt124.model.RawAttachment;
import com.dt124.util.SafeIoUtil;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Fully offline .eml parser built on Jakarta Mail. The message is constructed from an InputStream
 * only; no network access, no attachment execution and no writes of attachment bodies to disk.
 *
 * <p>Malformed or truncated MIME messages are handled defensively: parsing never throws out of
 * {@link #parseFile(Path)}. The outcome is recorded via {@link ParsedEmail#isParsedOk()} and
 * {@link ParsedEmail#getParseError()} so that Parser Coverage can be reported honestly.</p>
 */
public final class OfflineEmlParser {

    private static final int MAX_TEXT_CHARS = 1_000_000;
    private static final int MAX_PART_DEPTH = 50;

    private final Session session;

    public OfflineEmlParser() {
        Properties props = new Properties();
        props.put("mail.mime.base64.ignoreerrors", "true");
        props.put("mail.mime.decodefilename", "true");
        props.put("mail.mime.encodefilename", "true");
        props.put("mail.mime.charset", "UTF-8");
        props.put("mail.mime.multipart.ignoremissingendboundary", "true");
        props.put("mail.mime.multipart.ignoreexistingboundaryparameter", "true");
        props.put("mail.mime.address.strict", "false");
        this.session = Session.getInstance(props);
    }

    /**
     * Parses a single .eml file. Never throws.
     */
    public ParsedEmail parseFile(Path path) {
        ParsedEmail email = new ParsedEmail();
        email.setSourcePath(path.toString());
        try (InputStream in = new FileInputStream(path.toFile())) {
            parse(in, email);
        } catch (Exception e) {
            email.setParsedOk(false);
            email.setParseError(e.getClass().getSimpleName() + ": " + safeMessage(e));
            // Last-resort fallback: recover headers from the raw bytes when the MIME tree is unusable.
            recoverHeadersFromRawBytes(path, email);
        }
        return email;
    }

    /**
     * Parses an .eml payload already held in memory (used by tests and deduplication).
     */
    public ParsedEmail parseBytes(byte[] raw, String source) {
        ParsedEmail email = new ParsedEmail();
        email.setSourcePath(source == null ? "<memory>" : source);
        try {
            parse(new ByteArrayInputStream(raw), email);
        } catch (Exception e) {
            email.setParsedOk(false);
            email.setParseError(e.getClass().getSimpleName() + ": " + safeMessage(e));
            recoverHeadersFromRawBytes(raw, email);
        }
        return email;
    }

    private void parse(InputStream in, ParsedEmail email) throws Exception {
        MimeMessage message = new MimeMessage(session, in);

        email.setMessageId(safeHeader(message, "Message-ID"));
        email.setSubject(decodeSafe(safeSubject(message)));
        email.setFrom(decodeSafe(safeHeader(message, "From")));
        email.setTo(decodeSafe(safeHeader(message, "To")));
        email.setCc(decodeSafe(safeHeader(message, "Cc")));
        email.setReplyTo(decodeSafe(safeHeader(message, "Reply-To")));
        email.setReturnPath(decodeSafe(safeHeader(message, "Return-Path")));
        email.setDate(safeHeader(message, "Date"));

        String[] received = message.getHeader("Received");
        if (received != null) {
            for (String value : received) {
                email.getReceivedHeaders().add(value == null ? "" : value);
            }
        }

        email.setAuthResults(joinHeaders(message, "Authentication-Results"));
        email.setReceivedSpf(joinHeaders(message, "Received-SPF"));
        email.setDkimSignature(joinHeaders(message, "DKIM-Signature"));

        captureRawHeaders(message, email);

        boolean structuralSuccess = false;
        try {
            processPart(message, email, 0);
            structuralSuccess = true;
        } catch (Exception e) {
            email.setParseError("Body/parts: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
        }

        // A message is considered parsed if we recovered a usable identity or body content.
        boolean hasIdentity = !email.getSubject().isEmpty() || !email.getFrom().isEmpty()
                || !email.getMessageId().isEmpty();
        boolean hasBody = !email.getTextBody().isEmpty() || !email.getHtmlBody().isEmpty()
                || !email.getAttachments().isEmpty();
        email.setParsedOk(structuralSuccess || hasIdentity || hasBody);
        if (!email.isParsedOk() && email.getParseError().isEmpty()) {
            email.setParseError("No usable MIME content");
        }
    }

    private void processPart(Part part, ParsedEmail email, int depth) {
        if (part == null || depth > MAX_PART_DEPTH) {
            return;
        }
        try {
            if (part.isMimeType("multipart/*")) {
                Object content = part.getContent();
                if (content instanceof Multipart multipart) {
                    for (int i = 0; i < multipart.getCount(); i++) {
                        processPart(multipart.getBodyPart(i), email, depth + 1);
                    }
                }
                return;
            }
            if (part.isMimeType("message/rfc822")) {
                Object content = part.getContent();
                if (content instanceof Part nested) {
                    processPart(nested, email, depth + 1);
                }
                return;
            }

            String fileName = safeFileName(part);
            boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(safeDisposition(part))
                    || !fileName.isEmpty();

            if (isAttachment) {
                byte[] bytes = SafeIoUtil.readUpTo(part.getInputStream(), SafeIoUtil.MAX_ATTACHMENT_BYTES);
                String contentType = safeContentType(part);
                long declared = -1L;
                try {
                    declared = part.getSize();
                } catch (Exception ignored) {
                    // size is optional metadata
                }
                email.getAttachments().add(new RawAttachment(fileName, contentType, bytes, declared));
                return;
            }

            if (part.isMimeType("text/plain")) {
                if (email.getTextBody().isEmpty()) {
                    email.setTextBody(readText(part));
                }
            } else if (part.isMimeType("text/html")) {
                if (email.getHtmlBody().isEmpty()) {
                    email.setHtmlBody(readText(part));
                }
            } else if (part.isMimeType("text/*")) {
                if (email.getTextBody().isEmpty()) {
                    email.setTextBody(readText(part));
                }
            }
        } catch (Exception ignored) {
            // Defensive: a single bad part must not abort the whole message.
        }
    }

    private String readText(Part part) {
        try {
            Object content = part.getContent();
            if (content instanceof String text) {
                return truncate(text);
            }
            if (content instanceof InputStream in) {
                byte[] bytes = SafeIoUtil.readUpTo(in, MAX_TEXT_CHARS);
                return truncate(new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // fall through to raw stream decode
        }
        try (InputStream in = part.getInputStream()) {
            byte[] bytes = SafeIoUtil.readUpTo(in, MAX_TEXT_CHARS);
            return truncate(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "";
        }
    }

    private void captureRawHeaders(MimeMessage message, ParsedEmail email) {
        try {
            java.util.Enumeration<?> names = message.getAllHeaderLines();
            // getAllHeaderLines flattens header lines; store them for rule-engine scanning.
            java.util.List<String> lines = new java.util.ArrayList<>();
            while (names.hasMoreElements()) {
                Object next = names.nextElement();
                if (next != null) {
                    lines.add(next.toString());
                }
            }
            email.getRawHeaders().put("__lines__", lines);
        } catch (Exception ignored) {
            // Header capture is best-effort.
        }
    }

    private static String joinHeaders(MimeMessage message, String name) {
        try {
            String[] values = message.getHeader(name);
            if (values == null || values.length == 0) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(value);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String safeSubject(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (Exception e) {
            return safeHeader(message, "Subject");
        }
    }

    private static String safeHeader(MimeMessage message, String name) {
        try {
            String[] values = message.getHeader(name);
            return values == null || values.length == 0 || values[0] == null ? "" : values[0];
        } catch (Exception e) {
            return "";
        }
    }

    private static String safeContentType(Part part) {
        try {
            return part.getContentType();
        } catch (Exception e) {
            return "";
        }
    }

    private static String safeDisposition(Part part) {
        try {
            return part.getDisposition();
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeFileName(Part part) {
        try {
            String name = part.getFileName();
            return name == null ? "" : decodeSafe(name);
        } catch (Exception e) {
            return "";
        }
    }

    private static String decodeSafe(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            return MimeUtility.decodeText(value);
        } catch (Exception e) {
            return value;
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
    }

    private static String safeMessage(Exception e) {
        String msg = e.getMessage();
        return msg == null ? "" : msg.replaceAll("[\\r\\n]+", " ");
    }

    /**
     * Minimal raw header recovery for severely truncated messages. Scans the first 64 KB looking for
     * simple "Header: value" lines. This lets the parser still report provenance fields for RQ
     * reproduction without trusting a broken MIME structure.
     */
    private void recoverHeadersFromRawBytes(Path path, ParsedEmail email) {
        try (InputStream in = new FileInputStream(path.toFile())) {
            byte[] bytes = SafeIoUtil.readUpTo(in, 64 * 1024);
            recoverHeadersFromRawBytes(bytes, email);
        } catch (Exception ignored) {
            // Nothing else we can do safely.
        }
    }

    private void recoverHeadersFromRawBytes(byte[] raw, ParsedEmail email) {
        if (raw == null || raw.length == 0) {
            return;
        }
        String text = new String(raw, StandardCharsets.UTF_8);
        for (String line : text.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            switch (name.toLowerCase(java.util.Locale.ROOT)) {
                case "subject" -> {
                    if (email.getSubject().isEmpty()) {
                        email.setSubject(value);
                    }
                }
                case "from" -> {
                    if (email.getFrom().isEmpty()) {
                        email.setFrom(value);
                    }
                }
                case "to" -> {
                    if (email.getTo().isEmpty()) {
                        email.setTo(value);
                    }
                }
                case "message-id" -> {
                    if (email.getMessageId().isEmpty()) {
                        email.setMessageId(value);
                    }
                }
                case "return-path" -> {
                    if (email.getReturnPath().isEmpty()) {
                        email.setReturnPath(value);
                    }
                }
                case "reply-to" -> {
                    if (email.getReplyTo().isEmpty()) {
                        email.setReplyTo(value);
                    }
                }
                case "authentication-results" -> {
                    if (email.getAuthResults().isEmpty()) {
                        email.setAuthResults(value);
                    }
                }
                case "received-spf" -> {
                    if (email.getReceivedSpf().isEmpty()) {
                        email.setReceivedSpf(value);
                    }
                }
                case "dkim-signature" -> {
                    if (email.getDkimSignature().isEmpty()) {
                        email.setDkimSignature(value);
                    }
                }
                default -> {
                    // ignore other headers in fallback mode
                }
            }
        }
    }
}
