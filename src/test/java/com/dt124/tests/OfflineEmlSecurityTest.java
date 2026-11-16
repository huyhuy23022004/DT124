package com.dt124.tests;

import com.dt124.extractor.EmailFeatureExtractor;
import com.dt124.extractor.ExtractionResult;
import com.dt124.extractor.SafeIndicatorRuleEngine;
import com.dt124.model.EmailFeatureRecord;
import com.dt124.model.ParsedEmail;
import com.dt124.parser.OfflineEmlParser;
import com.dt124.testutil.EmlFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Negative / security tests: malformed .eml files, spoofed headers, fake magic bytes, path
 * traversal file names and charset errors. The parser and extractors must never throw and must
 * flag the malicious signals.
 */
class OfflineEmlSecurityTest {

    private final OfflineEmlParser parser = new OfflineEmlParser();
    private final SafeIndicatorRuleEngine rules = new SafeIndicatorRuleEngine();

    @Test
    void truncatedEmlIsHandledWithoutException(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("truncated.eml");
        Files.writeString(file, "From: a@b.com\nSubject: Broken\nContent-Type: multipart/mixed; boundary=q\n\n--q\nContent-Type: text/plain\n\nno end boundary",
                StandardCharsets.UTF_8);

        ParsedEmail email = parser.parseFile(file);
        assertNotNull(email);
        assertEquals("Broken", email.getSubject());
    }

    @Test
    void invalidCharsetDoesNotThrow() {
        String eml = "From: a@b.com\nSubject: Charset\nContent-Type: text/plain; charset=\"NOT-A-REAL-CHARSET\"\n\nHello world";
        ParsedEmail email = parser.parseBytes(eml.getBytes(StandardCharsets.UTF_8), "charset");

        assertNotNull(email);
        assertTrue(email.getSourcePath().contains("charset"));
    }

    @Test
    void spoofedSenderIsFlaggedByFeatureExtractor() {
        EmailFeatureExtractor extractor = new EmailFeatureExtractor(rules, "test", EmailFeatureRecord.LABEL_PHISHING);
        ParsedEmail email = parser.parseBytes(EmlFixtures.PHISHING_EML.getBytes(StandardCharsets.UTF_8), "spoof");
        ExtractionResult result = extractor.extract(email);
        EmailFeatureRecord record = result.getFeatureRecord();

        assertEquals(1.0d, record.get("from_return_path_mismatch"), 0.0001d);
        assertEquals(1.0d, record.get("from_reply_to_mismatch"), 0.0001d);
        assertEquals(1.0d, record.get("spf_fail"), 0.0001d);
        assertEquals(1.0d, record.get("dmarc_fail"), 0.0001d);
        assertTrue(record.get("yara_hidden_login_form") > 0 || record.get("yara_eval_obfuscation") > 0);
    }

    @Test
    void fakeMagicBytesAttachmentIsFlagged() {
        EmailFeatureExtractor extractor = new EmailFeatureExtractor(rules, "test", EmailFeatureRecord.LABEL_PHISHING);
        String eml = EmlFixtures.phishingWithAttachment("invoice.pdf", EmlFixtures.peBytes());
        ParsedEmail email = parser.parseBytes(eml.getBytes(StandardCharsets.UTF_8), "magic");
        ExtractionResult result = extractor.extract(email);

        assertEquals(1.0d, result.getFeatureRecord().get("att_magic_mismatch"), 0.0001d);
        assertTrue(result.getFeatureRecord().get("att_max_entropy") > 0);
    }

    @Test
    void pathTraversalAttachmentNameIsFlagged(@TempDir Path dir) throws IOException {
        String eml = EmlFixtures.phishingWithAttachment("../../evil.exe", EmlFixtures.peBytes());
        Path file = dir.resolve("traversal.eml");
        Files.writeString(file, eml, StandardCharsets.UTF_8);

        ParsedEmail email = parser.parseFile(file);
        assertFalse(email.getAttachments().isEmpty());
        assertEquals(1.0d, (double) (email.getAttachments().get(0).getFileName().contains("..") ? 1 : 0), 0.0001d);

        EmailFeatureExtractor extractor = new EmailFeatureExtractor(rules, "test", EmailFeatureRecord.LABEL_PHISHING);
        ExtractionResult result = extractor.extract(email);
        assertEquals(1, result.getAttachmentAnalysis().getPathTraversalCount());
    }

    @Test
    void hugeAttachmentIsCappedInMemory() throws IOException {
        byte[] huge = new byte[6 * 1024 * 1024];
        byte[] retained = com.dt124.util.SafeIoUtil.readUpTo(new java.io.ByteArrayInputStream(huge));
        assertEquals(com.dt124.util.SafeIoUtil.MAX_ATTACHMENT_BYTES, retained.length);
    }

    @Test
    void extensionlessMaildirFileIsAccepted(@TempDir Path dir) throws IOException {
        Path msg = dir.resolve("12345");
        Files.writeString(msg, "From: x@y.com\nSubject: no extension\n\nbody", StandardCharsets.UTF_8);

        com.dt124.dataset.DatasetLoader loader = new com.dt124.dataset.DatasetLoader(parser,
                new EmailFeatureExtractor(rules, "maildir", EmailFeatureRecord.LABEL_BENIGN));
        var results = loader.load(dir, "maildir", EmailFeatureRecord.LABEL_BENIGN, true, 0,
                new com.dt124.dataset.LoadStatistics());

        assertEquals(1, results.size());
        assertEquals(EmailFeatureRecord.LABEL_BENIGN, results.get(0).getFeatureRecord().getLabel());
    }
}
