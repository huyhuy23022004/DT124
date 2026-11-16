package slidegen;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Converts the 5-minute speaker script Markdown into a printable DOCX. */
public final class ScriptDocx {

    public static void main(String[] args) throws Exception {
        Path root = findRoot();
        Path md = root.resolve("Slide/DT124_Kich_Ban_5_Phut.md");
        Path out = root.resolve("Slide/DT124_Kich_Ban_5_Phut.docx");
        List<String> lines = Files.readAllLines(md, StandardCharsets.UTF_8);

        try (XWPFDocument doc = new XWPFDocument()) {
            a4(doc);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.startsWith("|")) {
                    List<String> tableLines = new ArrayList<>();
                    while (i < lines.size() && lines.get(i).startsWith("|")) {
                        tableLines.add(lines.get(i));
                        i++;
                    }
                    i--;
                    addTable(doc, tableLines);
                    continue;
                }
                if (line.startsWith("# ")) {
                    heading(doc, line.substring(2), 20, "0F2740", 240, 120);
                } else if (line.startsWith("## ")) {
                    heading(doc, line.substring(3), 14, "0E7C86", 220, 80);
                } else if (line.startsWith("### ")) {
                    heading(doc, line.substring(4), 13, "0F2740", 180, 60);
                } else if (line.startsWith("- ")) {
                    bullet(doc, line.substring(2));
                } else if (line.startsWith("> ")) {
                    quote(doc, line.substring(2));
                } else if (line.startsWith("---")) {
                    // separator: skip
                } else if (line.isBlank()) {
                    blank(doc);
                } else {
                    body(doc, line);
                }
            }
            try (OutputStream os = Files.newOutputStream(out)) {
                doc.write(os);
            }
        }
        System.out.println("[script] wrote " + out);
        try (XWPFDocument check = new XWPFDocument(Files.newInputStream(out))) {
            long slides = check.getParagraphs().stream()
                    .filter(p -> p.getText().startsWith("Trang ")).count();
            System.out.println("[script] verified: paragraphs=" + check.getParagraphs().size()
                    + ", tables=" + check.getTables().size() + ", slide headings=" + slides);
        }
    }

    private static void a4(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz sz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        sz.setW(BigInteger.valueOf(11906));
        sz.setH(BigInteger.valueOf(16838));
        CTPageMar mar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        mar.setLeft(BigInteger.valueOf(1134));
        mar.setRight(BigInteger.valueOf(1134));
        mar.setTop(BigInteger.valueOf(1021));
        mar.setBottom(BigInteger.valueOf(1021));
    }

    private static void heading(XWPFDocument doc, String text, int size, String color,
                                int before, int after) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(before);
        p.setSpacingAfter(after);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(size);
        r.setColor(color);
        r.setFontFamily("Calibri");
    }

    private static void body(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(80);
        p.setAlignment(ParagraphAlignment.BOTH);
        addRuns(p, text, false, null);
    }

    private static void bullet(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(60);
        p.setIndentationLeft(300);
        XWPFRun marker = p.createRun();
        marker.setText("•  ");
        marker.setBold(true);
        marker.setColor("0E7C86");
        marker.setFontFamily("Calibri");
        marker.setFontSize(11);
        addRuns(p, text, false, null);
    }

    private static void quote(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(80);
        p.setIndentationLeft(300);
        addRuns(p, text, true, "5A6B7B");
    }

    private static void blank(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(0);
        XWPFRun r = p.createRun();
        r.setText("");
        r.setFontSize(6);
    }

    private static void addRuns(XWPFParagraph p, String text, boolean italic, String color) {
        boolean bold = false;
        int i = 0;
        while (i < text.length()) {
            int idx = text.indexOf("**", i);
            if (idx < 0) {
                run(p, text.substring(i), bold, italic, color);
                break;
            }
            if (idx > i) {
                run(p, text.substring(i, idx), bold, italic, color);
            }
            bold = !bold;
            i = idx + 2;
        }
    }

    private static void run(XWPFParagraph p, String text, boolean bold, boolean italic, String color) {
        if (text.isEmpty()) {
            return;
        }
        XWPFRun r = p.createRun();
        r.setText(text.replace("`", ""));
        r.setBold(bold);
        r.setItalic(italic);
        r.setFontFamily("Calibri");
        r.setFontSize(11);
        if (color != null) {
            r.setColor(color);
        }
    }

    private static void addTable(XWPFDocument doc, List<String> lines) {
        List<String[]> rows = new ArrayList<>();
        for (String line : lines) {
            String s = line.trim();
            if (s.startsWith("|")) {
                s = s.substring(1);
            }
            if (s.endsWith("|")) {
                s = s.substring(0, s.length() - 1);
            }
            String[] cells = s.split("\\|", -1);
            boolean separator = true;
            for (String cell : cells) {
                if (!cell.trim().matches("-{2,}")) {
                    separator = false;
                    break;
                }
            }
            if (separator) {
                continue;
            }
            String[] clean = new String[cells.length];
            for (int i = 0; i < cells.length; i++) {
                clean[i] = cells[i].trim().replace("**", "");
            }
            rows.add(clean);
        }
        if (rows.isEmpty()) {
            return;
        }
        int cols = rows.get(0).length;
        XWPFTable table = doc.createTable(rows.size(), cols);
        table.setWidth("9360");
        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow row = table.getRow(r);
            for (int c = 0; c < cols; c++) {
                XWPFTableCell cell = row.getCell(c);
                cell.removeParagraph(0);
                XWPFParagraph p = cell.addParagraph();
                XWPFRun run = p.createRun();
                String value = c < rows.get(r).length ? rows.get(r)[c] : "";
                run.setText(value);
                run.setFontFamily("Calibri");
                run.setFontSize(10);
                if (r == 0) {
                    run.setBold(true);
                    run.setColor("FFFFFF");
                    cell.setColor("0F2740");
                } else if (r % 2 == 0) {
                    cell.setColor("EAF0F5");
                }
            }
        }
        blank(doc);
    }

    private static Path findRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            if (Files.isRegularFile(dir.resolve("Slide/DT124_Kich_Ban_5_Phut.md"))) {
                return dir;
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }
        throw new IllegalStateException("Cannot locate Slide/DT124_Kich_Ban_5_Phut.md");
    }
}
