package com.dt124.parser;

import com.dt124.model.ParsedEmail;
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

class OfflineEmlParserTest {

    private final OfflineEmlParser parser = new OfflineEmlParser();

    @Test
    void parsesBenignHeadersAndBody(@TempDir Path dir) throws IOException {
        Path file = write(dir, "benign.eml", EmlFixtures.BENIGN_EML);
        ParsedEmail email = parser.parseFile(file);

        assertTrue(email.isParsedOk());
        assertEquals("Meeting notes for the west power desk", email.getSubject());
        assertTrue(email.getFrom().contains("kay.mann@enron.com"));
        assertTrue(email.getReturnPath().contains("kay.mann@enron.com"));
        assertTrue(email.getAuthResults().contains("spf=pass"));
        assertTrue(email.getTextBody().contains("review the attached schedule"));
        assertEquals(1, email.getReceivedHeaders().size());
    }

    @Test
    void parsesPhishingMultipartAndExtractsHtml(@TempDir Path dir) throws IOException {
        Path file = write(dir, "phish.eml", EmlFixtures.PHISHING_EML);
        ParsedEmail email = parser.parseFile(file);

        assertTrue(email.isParsedOk());
        assertTrue(email.getHtmlBody().contains("pay-ap.top"));
        assertTrue(email.getTextBody().contains("Verify your account"));
        assertEquals(2, email.getReceivedHeaders().size());
    }

    @Test
    void extractsAttachmentWithoutWritingToDisk(@TempDir Path dir) throws IOException {
        String eml = EmlFixtures.phishingWithAttachment("invoice.pdf", EmlFixtures.pdfBytes());
        ParsedEmail email = parser.parseBytes(eml.getBytes(StandardCharsets.UTF_8), "memory");

        assertTrue(email.isParsedOk());
        assertEquals(1, email.getAttachments().size());
        assertEquals("invoice.pdf", email.getAttachments().get(0).getFileName());
        assertEquals(128, email.getAttachments().get(0).getContent().length);
    }

    @Test
    void truncatedMessageDoesNotThrowAndIsReportedAsNotOk(@TempDir Path dir) throws IOException {
        String truncated = "From: bad@example.com\nSubject: half a message\nContent-Type: multipart/mixed; boundary=\"X\"\n\n--X\nContent-Type: text/plain\n\nunterminated";
        ParsedEmail email = parser.parseBytes(truncated.getBytes(StandardCharsets.UTF_8), "truncated");

        assertNotNull(email);
        // Headers must still be recovered even when the MIME tree is broken.
        assertEquals("half a message", email.getSubject());
        assertTrue(email.getSourcePath().equals("truncated"));
    }

    @Test
    void emptyInputIsHandledGracefully() {
        ParsedEmail email = parser.parseBytes(new byte[0], "empty");
        assertNotNull(email);
        assertFalse(email.getParseError() == null);
    }

    private Path write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
