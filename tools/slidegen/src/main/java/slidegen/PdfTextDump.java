package slidegen;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PdfTextDump {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && !Files.isRegularFile(root.resolve("tyag020.pdf")); i++) {
            root = root.getParent();
        }
        Path out = root.resolve("artifacts/figures/paper/full_text.txt");
        try (PDDocument doc = PDDocument.load(root.resolve("tyag020.pdf").toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            StringBuilder sb = new StringBuilder();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                sb.append("\n===== PAGE ").append(p).append(" =====\n");
                sb.append(stripper.getText(doc));
            }
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        }
        System.out.println("[pdf-text] wrote " + out);
    }
}
