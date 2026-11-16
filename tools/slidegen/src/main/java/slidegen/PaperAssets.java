package slidegen;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Renders the exact regions of tyag020.pdf used as evidence in the deck. Coordinates are in PDF
 * points, measured from the top-left of the rendered page (PDFBox renders upright).
 */
public final class PaperAssets {

    private static final int DPI = 300;

    public static void main(String[] args) throws Exception {
        Path root = PdfFigureExtractorRoot.find();
        Path pdf = root.resolve("tyag020.pdf");
        Path assets = root.resolve("assets/paper");
        Files.createDirectories(assets);

        try (PDDocument doc = PDDocument.load(pdf.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);

            // Page 1: journal header + title + authors + abstract + keywords.
            crop(renderer, 0, 18.0, 18.0, 540.0, 380.0, assets.resolve("paper_page1.png"));

            // Page 15: Table 12 - Google Safe Browsing detection (RQ4 evidence).
            crop(renderer, 14, 30.0, 40.0, 540.0, 130.0, assets.resolve("table12_gsb.png"));

            // Page 8: Table 1 - dataset summary (15,975 FQDNs / 15,126 domains).
            crop(renderer, 7, 30.0, 40.0, 540.0, 88.0, assets.resolve("table1_dataset.png"));
        }

        Path xobj = root.resolve("artifacts/figures/paper/xobjects");
        Files.copy(xobj.resolve("page-04-img-01.png"), assets.resolve("figure1_compromised.png"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(xobj.resolve("page-04-img-02.png"), assets.resolve("figure2_nrd.png"),
                StandardCopyOption.REPLACE_EXISTING);

        System.out.println("[paper-assets] written to " + assets);
        try (var stream = Files.list(assets)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".png"))
                    .forEach(p -> System.out.println("  - " + p.getFileName()));
        }
    }

    private static void crop(PDFRenderer renderer, int page, double xPt, double yPt,
                             double wPt, double hPt, Path out) throws Exception {
        BufferedImage pageImg = renderer.renderImageWithDPI(page, DPI, ImageType.RGB);
        int x = (int) Math.round(xPt / 72.0d * DPI);
        int y = (int) Math.round(yPt / 72.0d * DPI);
        int w = (int) Math.round(wPt / 72.0d * DPI);
        int h = (int) Math.round(hPt / 72.0d * DPI);
        x = Math.max(0, Math.min(x, pageImg.getWidth() - 1));
        y = Math.max(0, Math.min(y, pageImg.getHeight() - 1));
        w = Math.max(1, Math.min(w, pageImg.getWidth() - x));
        h = Math.max(1, Math.min(h, pageImg.getHeight() - y));
        BufferedImage crop = pageImg.getSubimage(x, y, w, h);
        ImageIO.write(crop, "png", out.toFile());
        System.out.printf("[paper-assets] %s  crop=%dx%d px (page %d)%n",
                out.getFileName(), w, h, page + 1);
    }

    private static final class PdfFigureExtractorRoot {
        static Path find() {
            Path dir = Path.of("").toAbsolutePath();
            for (int i = 0; i < 8; i++) {
                if (Files.isRegularFile(dir.resolve("tyag020.pdf"))
                        || Files.isRegularFile(dir.resolve("artifacts/reports/model_comparison.csv"))) {
                    return dir;
                }
                dir = dir.getParent();
                if (dir == null) {
                    break;
                }
            }
            throw new IllegalStateException("Cannot locate repository root");
        }
    }
}
