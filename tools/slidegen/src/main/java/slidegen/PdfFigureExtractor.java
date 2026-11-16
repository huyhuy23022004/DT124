package slidegen;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Development helper: renders tyag020.pdf pages and extracts embedded images so the real paper
 * figures can be located and cropped without guessing.
 */
public final class PdfFigureExtractor {

    public static void main(String[] args) throws Exception {
        Path root = findRoot();
        Path pdf = args.length > 0 ? Path.of(args[0]) : root.resolve("tyag020.pdf");
        Path out = root.resolve("artifacts/figures/paper");
        Files.createDirectories(out.resolve("pages"));
        Files.createDirectories(out.resolve("xobjects"));

        try (PDDocument doc = PDDocument.load(pdf.toFile())) {
            int pages = doc.getNumberOfPages();
            System.out.println("[pdf] " + pdf + " pages=" + pages);
            PDFRenderer renderer = new PDFRenderer(doc);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int i = 0; i < pages; i++) {
                PDPage page = doc.getPage(i);
                String text = "";
                try {
                    stripper.setStartPage(i + 1);
                    stripper.setEndPage(i + 1);
                    text = stripper.getText(doc).replaceAll("\\s+", " ").trim();
                } catch (Exception e) {
                    text = "(text extraction failed: " + e.getMessage() + ")";
                }
                if (text.length() > 420) {
                    text = text.substring(0, 420) + "...";
                }
                System.out.printf(Locale.ROOT, "===== PAGE %d  (%.1f x %.1f pt) =====%n%s%n",
                        i + 1, page.getMediaBox().getWidth(), page.getMediaBox().getHeight(), text);

                BufferedImage img = renderer.renderImageWithDPI(i, 150, ImageType.RGB);
                ImageIO.write(img, "png", out.resolve("pages").resolve(String.format(Locale.ROOT, "page-%02d.png", i + 1)).toFile());

                PDResources res = page.getResources();
                if (res == null) {
                    continue;
                }
                int k = 0;
                for (COSName name : res.getXObjectNames()) {
                    PDXObject x = res.getXObject(name);
                    if (x instanceof PDImageXObject imgObj) {
                        k++;
                        BufferedImage bi = imgObj.getImage();
                        Path target = out.resolve("xobjects").resolve(
                                String.format(Locale.ROOT, "page-%02d-img-%02d.png", i + 1, k));
                        ImageIO.write(bi, "png", target.toFile());
                        System.out.printf(Locale.ROOT, "  [xobject] %s %dx%d -> %s%n",
                                name.getName(), bi.getWidth(), bi.getHeight(), target.getFileName());
                    }
                }
            }
        }
        System.out.println("[pdf] outputs -> " + out);
    }

    private static Path findRoot() {
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
