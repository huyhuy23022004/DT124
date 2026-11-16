package com.dt124.extractor;

import com.dt124.model.AttachmentMeta;
import com.dt124.model.RawAttachment;
import com.dt124.testutil.EmlFixtures;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeAttachmentInspectorTest {

    private final SafeAttachmentInspector inspector = new SafeAttachmentInspector();

    @Test
    void detectsMagicByteMismatchForPdfNamedExecutable() {
        RawAttachment raw = new RawAttachment("statement.pdf", "application/pdf", EmlFixtures.peBytes(), 128);
        AttachmentAnalysis analysis = inspector.inspect(List.of(raw));

        AttachmentMeta meta = analysis.getAttachments().get(0);
        assertEquals("pe-executable", meta.getDetectedType());
        assertTrue(meta.isMagicMismatch());
    }

    @Test
    void detectsDoubleExtension() {
        RawAttachment raw = new RawAttachment("invoice.pdf.exe", "application/octet-stream",
                EmlFixtures.peBytes(), 128);
        AttachmentAnalysis analysis = inspector.inspect(List.of(raw));

        assertTrue(analysis.getAttachments().get(0).isDoubleExtension());
        assertTrue(analysis.getAttachments().get(0).isDangerousExtension());
    }

    @Test
    void detectsPathTraversalFilename() {
        RawAttachment raw = new RawAttachment("../../evil.exe", "application/octet-stream",
                EmlFixtures.peBytes(), 128);
        AttachmentAnalysis analysis = inspector.inspect(List.of(raw));

        assertTrue(analysis.getAttachments().get(0).isPathTraversal());
    }

    @Test
    void safePdfHasNoMismatch() {
        RawAttachment raw = new RawAttachment("receipt.pdf", "application/pdf", EmlFixtures.pdfBytes(), 128);
        AttachmentAnalysis analysis = inspector.inspect(List.of(raw));

        AttachmentMeta meta = analysis.getAttachments().get(0);
        assertEquals("pdf", meta.getDetectedType());
        assertFalse(meta.isMagicMismatch());
        assertFalse(meta.isDoubleExtension());
    }

    @Test
    void entropyIsHigherForEncryptedPayload() {
        byte[] zeros = new byte[256];
        byte[] random = new byte[256];
        for (int i = 0; i < random.length; i++) {
            random[i] = (byte) (i * 73 + 17);
        }
        AttachmentAnalysis low = inspector.inspect(List.of(new RawAttachment("a.bin", "application/octet-stream", zeros, 256)));
        AttachmentAnalysis high = inspector.inspect(List.of(new RawAttachment("b.bin", "application/octet-stream", random, 256)));

        assertTrue(high.getMaxEntropy() > low.getMaxEntropy());
        assertEquals(0.0d, low.getMaxEntropy(), 0.0001d);
    }

    @Test
    void detectsHtmlDisguisedAsPdf() {
        byte[] html = "<html><body>hi</body></html>".getBytes(StandardCharsets.UTF_8);
        RawAttachment raw = new RawAttachment("doc.pdf", "application/pdf", html, html.length);
        AttachmentAnalysis analysis = inspector.inspect(List.of(raw));

        assertTrue(analysis.getAttachments().get(0).isMagicMismatch());
        assertEquals("html-document", analysis.getAttachments().get(0).getDetectedType());
    }
}
