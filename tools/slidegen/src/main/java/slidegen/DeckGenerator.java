package slidegen;

import org.apache.poi.sl.usermodel.PictureData.PictureType;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.util.Units;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Builds a NEW, self-contained DT124 midterm deck (does not touch the existing .pptx).
 * Data is read from artifacts/reports/*.csv, figures are rendered with Java2D.
 */
public final class DeckGenerator {

    private static final double W = 960.0d;
    private static final double H = 540.0d;

    private static final Color NAVY = new Color(0x0F2740);
    private static final Color NAVY2 = new Color(0x17395A);
    private static final Color TEAL = new Color(0x0E7C86);
    private static final Color ORANGE = new Color(0xE07A2F);
    private static final Color GREEN = new Color(0x1E8449);
    private static final Color RED = new Color(0xB03A2E);
    private static final Color TEXT = new Color(0x1B2430);
    private static final Color MUTED = new Color(0x5A6B7B);
    private static final Color LIGHT = new Color(0xEAF0F5);
    private static final Color BG = new Color(0xF6F8FB);
    private static final Color WHITE = Color.WHITE;
    private static final Color LINE = new Color(0xD5DEE7);

    private static final String FONT = "Segoe UI";

    private static int page = 0;
    private static final int TOTAL = 11;

    public static void main(String[] args) throws Exception {
        Path root = findRoot();
        Path reports = root.resolve("artifacts/reports");
        Path figures = root.resolve("artifacts/figures");
        Path slideDir = root.resolve("Slide");
        Files.createDirectories(figures);
        Files.createDirectories(slideDir);

        Map<String, Map<String, String>> modelCsv = readCsvMap(reports.resolve("model_comparison.csv"));
        List<Map<String, String>> ablationCsv = readCsvList(reports.resolve("ablation.csv"));
        List<Map<String, String>> confusionCsv = readCsvList(reports.resolve("confusion_matrix.csv"));
        Map<String, Map<String, String>> replicationCsv = readCsvMap(reports.resolve("replication.csv"));
        List<Map<String, String>> tldCsv = readCsvList(reports.resolve("tld_distribution.csv"));

        MR baseline = model(modelCsv, "Baseline (NaiveBayes)", "Mô hình cơ sở");
        MR paper = model(modelCsv, "Paper URL-Only (RandomForest)", "Chỉ dùng URL");
        MR proposed = model(modelCsv, "Proposed Hybrid Full (RF + Rule)", "Mô hình đề xuất");
        MR[] models = {baseline, paper, proposed};

        Confusion cmBaseline = confusion(confusionCsv, "Baseline (NaiveBayes)");
        Confusion cmPaper = confusion(confusionCsv, "Paper URL-Only (RandomForest)");
        Confusion cmProposed = confusion(confusionCsv, "Proposed Hybrid Full (RF + Rule)");
        Confusion[] cms = {cmBaseline, cmPaper, cmProposed};

        double[] ablF1 = new double[ablationCsv.size()];
        double[] ablRecall = new double[ablationCsv.size()];
        double[] ablMcc = new double[ablationCsv.size()];
        String[] ablNames = new String[ablationCsv.size()];
        String[] ablShort = new String[ablationCsv.size()];
        for (int i = 0; i < ablationCsv.size(); i++) {
            Map<String, String> row = ablationCsv.get(i);
            ablNames[i] = row.get("model").replace("Ablation: ", "");
            ablShort[i] = shortAblation(ablNames[i]);
            ablF1[i] = d(row, "f1_mean");
            ablRecall[i] = d(row, "recall_mean");
            ablMcc[i] = d(row, "mcc_mean");
        }

        double freeHost = dv(replicationCsv, "free_hosting_percent");
        double brandHyphen = dv(replicationCsv, "brand_hyphen_percent");
        double subdomain = dv(replicationCsv, "subdomain_phishing_percent");
        double https = dv(replicationCsv, "valid_tls_percent_proxy");
        double uniqueHosts = dv(replicationCsv, "unique_domains");
        double emails = dv(replicationCsv, "emails_analysed");

        String[] tlds = new String[tldCsv.size()];
        double[] tldCounts = new double[tldCsv.size()];
        for (int i = 0; i < tldCsv.size(); i++) {
            tlds[i] = "." + tldCsv.get(i).get("tld");
            tldCounts[i] = d(tldCsv.get(i), "count");
        }

        Path figModels = figures.resolve("fig_model_metrics.png");
        Path figAblation = figures.resolve("fig_ablation.png");
        Path figConfusion = figures.resolve("fig_confusion.png");
        Path figTld = figures.resolve("fig_tld.png");
        Path figRq = figures.resolve("fig_rq_proxy.png");

        groupedBars(figModels, "So sánh mô hình (trung bình 3 lần chạy)",
                "Kiểm định chéo 5 phần × 3 lần chạy (42/100/2026) – lớp dương = thư lừa đảo",
                new String[]{"F1", "ROC-AUC", "PR-AUC", "Độ đặc hiệu"},
                new String[]{baseline.shortName, paper.shortName, proposed.shortName},
                new double[][]{
                        {baseline.f1, baseline.roc, baseline.pr, baseline.spec},
                        {paper.f1, paper.roc, paper.pr, paper.spec},
                        {proposed.f1, proposed.roc, proposed.pr, proposed.spec}},
                new Color[]{new Color(0x8FA6B8), ORANGE, TEAL}, 1.0d);
        groupedBars(figAblation, "Phân tích loại bỏ đặc trưng (F1 / Độ bao phủ / MCC)",
                "Bỏ từng nhóm đặc trưng – mô hình kết hợp",
                ablShort,
                new String[]{"F1", "Bao phủ", "MCC"},
                new double[][]{ablF1, ablRecall, ablMcc},
                new Color[]{TEAL, ORANGE, NAVY2}, 1.0d);
        confusionPanel(figConfusion, models, cms);
        groupedBars(figTld, "Tên miền cấp cao bị lạm dụng trong tập dữ liệu ngoại tuyến",
                "Thống kê trên tập .eml của đề tài",
                tlds, new String[]{"Số thư"},
                new double[][]{tldCounts}, new Color[]{TEAL}, max(tldCounts) * 1.15d);
        horizontalBars(figRq, "Chỉ số tái lập được từ nội dung .eml (%)",
                new String[]{"URL HTTPS", "Lưu trữ miễn phí", "Thương hiệu + gạch nối", "Tên miền con"},
                new double[]{https, freeHost, brandHyphen, subdomain},
                new Color[]{TEAL, ORANGE, NAVY2, new Color(0x8E6FA8)}, 100.0d);

        XMLSlideShow ppt = new XMLSlideShow();
        ppt.setPageSize(new Dimension((int) W, (int) H));

        Path paperDir = root.resolve("assets/paper");
        s01Title(ppt);
        s02Overview(ppt, paperDir);
        s03MethodData(ppt, paperDir);
        s04Replication(ppt, paperDir, uniqueHosts, emails, https, freeHost, brandHyphen, subdomain);
        s05LimitsArchitecture(ppt);
        designSlide(ppt);
        s07Results(ppt, models, cms);
        s08ChartsAblation(ppt, figModels, figAblation, ablNames, ablRecall, ablF1, ablMcc);
        errorCostSlide(ppt, figTld);
        conclusionSlide(ppt, models);
        s11Closing(ppt);

        Path out = slideDir.resolve("DT124_Bao_Cao_Giua_Ky_v2.pptx");
        try (var fos = Files.newOutputStream(out)) {
            ppt.write(fos);
        }
        ppt.close();

        System.out.println("[slidegen] wrote " + out);
        System.out.println("[slidegen] figures -> " + figures);
        System.out.println("[slidegen] slides: " + TOTAL);
        verify(out);
        render(out, figures.resolve("render"));
    }

    private static void render(Path pptx, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        try (XMLSlideShow show = new XMLSlideShow(Files.newInputStream(pptx))) {
            int index = 0;
            for (XSLFSlide slide : show.getSlides()) {
                index++;
                BufferedImage img = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setColor(WHITE);
                g.fillRect(0, 0, 1920, 1080);
                g.scale(2.0d, 2.0d);
                slide.draw(g);
                g.dispose();
                ImageIO.write(img, "png", outDir.resolve(String.format(Locale.ROOT, "slide%02d.png", index)).toFile());
            }
            System.out.println("[render] wrote " + index + " previews to " + outDir);
        }
    }

    private static void verify(Path pptx) throws IOException {
        try (XMLSlideShow show = new XMLSlideShow(Files.newInputStream(pptx))) {
            int index = 0;
            int warnings = 0;
            for (XSLFSlide slide : show.getSlides()) {
                index++;
                for (var shape : slide.getShapes()) {
                    Rectangle2D a = shape.getAnchor();
                    if (a == null) {
                        continue;
                    }
                    double bottom = a.getY() + a.getHeight();
                    double right = a.getX() + a.getWidth();
                    if (a.getX() < -1 || a.getY() < -1 || right > 961 || bottom > 541) {
                        warnings++;
                        System.out.printf(Locale.ROOT,
                                "[verify] slide %d out-of-bounds: %s x=%.1f y=%.1f w=%.1f h=%.1f%n",
                                index, shape.getClass().getSimpleName(), a.getX(), a.getY(),
                                a.getWidth(), a.getHeight());
                    }
                    if (shape instanceof XSLFTable) {
                        System.out.printf(Locale.ROOT,
                                "[verify] slide %d table y=%.1f h=%.1f bottom=%.1f%n",
                                index, a.getY(), a.getHeight(), bottom);
                    }
                }
            }
            System.out.println("[verify] reopened deck, slides=" + index + ", out-of-bounds warnings=" + warnings);
        }
    }

    // ------------------------------------------------------------------ slides

    private static void titleSlide(XMLSlideShow ppt, Path root) {
        XSLFSlide s = newSlide(ppt, BG);
        rect(s, 0, 0, W, 8, TEAL);
        rect(s, 0, H - 8, W, 8, TEAL);
        rect(s, 64, 112, 6, 210, ORANGE);
        text(s, 88, 108, 800, 30, 13, true, TEAL, TextAlign.LEFT,
                "BÁO CÁO GIỮA KỲ  ·  ĐỀ TÀI 124  ·  JAVA / MAVEN");
        text(s, 88, 150, 850, 120, 36, true, NAVY, TextAlign.LEFT,
                "Phân tích thư điện tử lừa đảo ngoại tuyến\ntừ tệp\u00A0.eml");
        text(s, 88, 278, 800, 70, 18, false, MUTED, TextAlign.LEFT,
                "Tái lập kết quả bài báo tyag020 (CH1–CH4) + mô hình học máy cải tiến");
        chip(s, 88, 372, 190, 34, new Color(0xE3F1F2), "100% ngoại tuyến", TEAL);
        chip(s, 290, 372, 210, 34, new Color(0xE3F1F2), "Không thực thi mã", TEAL);
        chip(s, 512, 372, 230, 34, new Color(0xE3F1F2), "38/38 bài kiểm thử đạt", TEAL);
        text(s, 88, 448, 760, 26, 13, false, MUTED, TextAlign.LEFT,
                "Sinh viên: Bùi Ngọc Huy   |   GVHD: Phạm Trọng Huynh   |   11/2026");
        text(s, 88, 476, 760, 22, 11, false, MUTED, TextAlign.LEFT,
                "DOI 10.1093/cybsec/tyag020  ·  thư viện Weka 3.8.6  ·  JDK 21 (Temurin)");
    }

    private static void agendaSlide(XMLSlideShow ppt) {
        XSLFSlide s = contentSlide(ppt, "NỘI DUNG TRÌNH BÀY", "Lộ trình báo cáo giữa kỳ");
        String[][] items = {
                {"1", "Tổng quan bài báo & mục tiêu đề tài", "slide 3–4"},
                {"2", "Tái lập RQ1–RQ4: Paper vs Offline proxy", "slide 5–6"},
                {"3", "Chuyển hoá offline: 4 module Java", "slide 7–8"},
                {"4", "Dữ liệu, provenance & manifest", "slide 9"},
                {"5", "Mô hình cải tiến & đánh giá định lượng", "slide 10–15"},
                {"6", "Kết luận, hạn chế & hướng phát triển", "slide 16–17"}};
        double y = 108;
        for (String[] item : items) {
            rect(s, 60, y, 840, 58, WHITE);
            rect(s, 60, y, 5, 58, TEAL);
            circle(s, 82, y + 12, 34, NAVY);
            text(s, 82, y + 17, 34, 24, 15, true, WHITE, TextAlign.CENTER, item[0]);
            text(s, 132, y + 16, 520, 28, 15, true, TEXT, TextAlign.LEFT, item[1]);
            text(s, 760, y + 19, 126, 22, 11, false, MUTED, TextAlign.RIGHT, item[2]);
            y += 68;
        }
    }

    private static void overviewSlide(XMLSlideShow ppt) {
        XSLFSlide s = contentSlide(ppt, "1. TỔNG QUAN BÀI BÁO & MỤC TIÊU",
                "Examining newly registered phishing domains at scale");
        card(s, 48, 106, 404, 300, WHITE);
        text(s, 70, 122, 360, 22, 11, true, TEAL, TextAlign.LEFT, "BÀI BÁO GỐC");
        bullets(s, 70, 152, 360, new String[]{
                "Dịch: Nghiên cứu quy mô lớn các tên miền lừa đảo mới đăng ký",
                "Agarwal & Vasek (2026) · 15.126 tên miền mới / 11 tháng",
                "Nguồn: WHOIS, Passive DNS, Certificate Transparency, ASN, GSB",
                "Phạm vi: 15.126 domain phishing mới đăng ký",
                "4 câu hỏi nghiên cứu (RQ1–RQ4)",
                "Điểm mạnh: quy mô lớn, đa nguồn",
                "Điểm yếu: phụ thuộc truy vấn online"}, 12.5);

        card(s, 468, 106, 444, 300, WHITE);
        text(s, 490, 122, 400, 22, 11, true, ORANGE, TextAlign.LEFT, "MỤC TIÊU ĐỀ TÀI 124");
        bullets(s, 490, 152, 402, new String[]{
                "Dịch bài báo và tái lập RQ1–RQ4 trên",
                "  phần dữ liệu có thể suy ra từ tệp .eml",
                "Dựng lại mô hình dữ liệu 20+ trường",
                "Xây pipeline Java offline, zero-execution",
                "Cải tiến mô hình ML vượt baseline",
                "  (mục tiêu điểm 9–10)"}, 12.5);

        double x = 48;
        String[][] rqs = {
                {"CH1", "Hạ tầng, TLS, Cloudflare, lưu trữ miễn phí"},
                {"CH2", "Thương hiệu, gạch nối, tên miền cấp cao bị lạm dụng"},
                {"CH3", "Vòng đời tên miền, tái đăng ký"},
                {"CH4", "Điểm mù tên miền con của GSB"}};
        for (String[] rq : rqs) {
            card(s, x, 420, 208, 74, NAVY);
            text(s, x + 12, 430, 60, 20, 11, true, new Color(0xFFC58A), TextAlign.LEFT, rq[0]);
            text(s, x + 12, 452, 184, 34, 10, false, new Color(0xD5E1EC), TextAlign.LEFT, rq[1]);
            x += 218;
        }
    }

    private static void paperMethodSlide(XMLSlideShow ppt) {
        XSLFSlide s = contentSlide(ppt, "1. PHƯƠNG PHÁP & PIPELINE CỦA BÀI BÁO",
                "Từ thu thập NRD đến làm giàu đa nguồn, rồi trả lời 4 RQ");
        String[] steps = {
                "Thu thập NRD\n15.126 domain\nphishing mới đăng ký",
                "Làm giàu\nWHOIS / Passive DNS\n/ CT logs / ASN / GSB",
                "Phân tích\nTLS, Cloudflare,\nfree hosting, hyphen",
                "4 RQ\nHạ tầng – Thương hiệu\n– Vòng đời – GSB"};
        Color[] cols = {TEAL, NAVY2, ORANGE, new Color(0x8E6FA8)};
        double x = 48;
        for (int i = 0; i < steps.length; i++) {
            box(s, x, 118, 188, 118, cols[i], WHITE, steps[i]);
            if (i < steps.length - 1) {
                arrow(s, x + 192, 163, 20, 28, MUTED);
            }
            x += 216;
        }

        card(s, 48, 262, 864, 96, WHITE);
        text(s, 68, 276, 820, 22, 11, true, TEAL, TextAlign.LEFT, "ĐIỂM CHỐT: BÀI BÁO DÙNG TRUY VẤN ONLINE");
        text(s, 68, 300, 820, 46, 12, false, TEXT, TextAlign.LEFT,
                "WHOIS / Passive DNS / CT logs / GSB đều là truy vấn online. Đề tài 124 thay bằng đặc trưng tĩnh\n"
                        + "trích xuất trực tiếp từ header, URL và tệp đính kèm của .eml → 100% offline, không thực thi mã.");

        card(s, 48, 374, 864, 108, LIGHT);
        text(s, 68, 388, 820, 22, 11, true, NAVY, TextAlign.LEFT, "ÁNH XẠ SANG ĐỀ TÀI 124");
        text(s, 68, 414, 820, 58, 12, false, TEXT, TextAlign.LEFT,
                "Thông tin hạ tầng  →  SPF/DKIM/DMARC, From/Return-Path, TLS-URL proxy\n"
                        + "Thông tin thương hiệu  →  brand + hyphen, TLD, free-hosting, độ sâu subdomain\n"
                        + "Thông tin nội dung  →  keyword phishing, số URL, magic-byte mismatch, 8 luật YARA-style");
    }

    private static void dataModelSlide(XMLSlideShow ppt) {
        XSLFSlide s = contentSlide(ppt, "2. MÔ HÌNH DỮ LIỆU TÁI LẬP",
                "PaperDomainRecord: 20+ trường, mỗi nhóm phục vụ một RQ");
        String[] headers = {"Nhóm trường", "Ví dụ", "Phục vụ"};
        String[][] rows = {
                {"Định danh & nguồn", "domain, first_seen, source, label", "Tất cả"},
                {"Hạ tầng", "tls_valid, cert_count, asn, cloudflare", "CH1"},
                {"Thương hiệu", "brand, hyphen, tld, free_hosting", "CH2"},
                {"Vòng đời", "whois_age, reregistered, dns_active_days", "CH3"},
                {"Phân giải", "subdomain_depth, gsb_listed, browser_verdict", "CH4"},
                {"Nội dung thư", "subject/body keyword, url_count, auth results", "Tất cả"}};
        table(s, 48, 112, 864, headers, rows,
                new double[]{210, 500, 90}, 12.0, 12.5, new int[]{0});

        card(s, 48, 372, 864, 112, WHITE);
        text(s, 68, 386, 820, 22, 11, true, TEAL, TextAlign.LEFT, "VÌ SAO PHẢI DỰNG LẠI MÔ HÌNH DỮ LIỆU?");
        text(s, 68, 412, 820, 60, 12, false, TEXT, TextAlign.LEFT,
                "Bài báo mô tả 20+ trường nhưng không phát hành dataset. Chúng tôi dựng lại schema và sinh dữ liệu\n"
                        + "có kiểm soát, để mọi trường đều có thể đo được offline. Nhờ vậy phần tái lập là trung thực:\n"
                        + "trường nào cần truy vấn online (WHOIS lịch sử, CT logs, GSB) được đánh dấu n/a thay vì đoán bừa.");
    }

    private static void rqTableSlide(XMLSlideShow ppt, double hosts, double emails, double https,
                                     double freeHost, double brandHyphen, double subdomain) {
        XSLFSlide s = contentSlide(ppt, "2. ĐỐI CHIẾU TÁI LẬP RQ1–RQ4",
                "Chỉ báo cáo phần suy ra được offline; trường cần online được đánh dấu n/a");
        String[] headers = {"CH", "Chỉ số", "Bài báo tyag020", "Đề tài 124 (ngoại tuyến)", "Đánh giá"};
        String[][] rows = {
                {"CH1", "Tên miền mới lừa đảo", "15.126 (11 tháng)", n0(emails) + " thư / " + n0(hosts) + " máy chủ", "thay thế"},
                {"CH1", "TLS hợp lệ", "79.3%", pct(https) + " (URL HTTPS thay thế)", "khác định nghĩa"},
                {"CH1", "Tên miền > 50 chứng chỉ", "190", "không có (thiếu nhật ký CT)", "không có"},
                {"CH1", "Cloudflare (ủy nhiệm)", "26%", "không có (thiếu DNS/ASN)", "không có"},
                {"CH1", "Lạm dụng lưu trữ miễn phí", "định tính", pct(freeHost), "tái lập"},
                {"CH2", "Thương hiệu + gạch nối", "pay-ap[.]com", pct(brandHyphen), "tái lập"},
                {"CH3", "Vòng đời TB", "8.6 ngày (so với 21 giờ)", "n/a (cần DNS dọc thời gian)", "không có"},
                {"CH3", "Tái đăng ký", "25.7%", "n/a (cần WHOIS lịch sử)", "không có"},
                {"CH4", "Điểm mù tên miền con", "API vs browser", pct(subdomain) + " thư", "thay thế"}};
        table(s, 40, 104, 880, headers, rows,
                new double[]{48, 168, 150, 300, 150}, 10.0, 10.5, new int[]{0});
    }

    private static void limitationsSlide(XMLSlideShow ppt) {
        XSLFSlide s = contentSlide(ppt, "3. 5 HẠN CHẾ BÀI BÁO → 4 MODULE JAVA",
                "Mỗi hạn chế được xử lý bằng một thành phần offline cụ thể");
        String[] headers = {"Hạn chế của bài báo", "Giải pháp trong đề tài", "Module / class"};
        String[][] rows = {
                {"Phụ thuộc WHOIS/DNS/CT trực tuyến", "Trích xuất tĩnh từ header, URL, attachment", "OfflineEmlParser, UrlDomainAnalyzer"},
                {"Chỉ phân tích domain, bỏ qua email", "Đặc trưng subject/body/keyword", "EmailFeatureExtractor"},
                {"Không kiểm tra xác thực email", "SPF/DKIM/DMARC + sai lệch", "AuthHeaderValidator"},
                {"Không phân tích tệp đính kèm", "Magic-byte, macro, 8 luật YARA-style", "SafeAttachmentInspector, SafeIndicatorRuleEngine"},
                {"Không có mô hình ML tái sử dụng", "30 đặc trưng + Weka + hybrid rule", "FeatureSchema, HybridScoringClassifier"}};
        table(s, 40, 112, 880, headers, rows,
                new double[]{250, 330, 300}, 10.5, 11.5, new int[]{0});

        card(s, 40, 372, 880, 112, WHITE);
        text(s, 58, 386, 844, 22, 11, true, ORANGE, TextAlign.LEFT, "KẾT QUẢ: 100% OFFLINE, KHÔNG GỌI MẠNG");
        text(s, 58, 412, 844, 60, 12, false, TEXT, TextAlign.LEFT,
                "44 tệp Java chính + 10 tệp test; 38/38 test pass. Parser giới hạn 5 MB/tệp, kiểm tra charset, magic bytes,\n"
                        + "và chặn path traversal. Không resolve DNS, không mở URL, không thực thi macro → an toàn khi demo.");
    }

    private static void architectureSlide(XMLSlideShow ppt) {
        XSLFSlide s = contentSlide(ppt, "3. KIẾN TRÚC PIPELINE JAVA + ZERO-EXECUTION",
                "Từ .eml đến báo cáo, tất cả chạy cục bộ");
        String[] steps = {
                "Input\n.eml",
                "Parser\n5 MB cap, charset,\npath traversal",
                "Extractor\n5 họ đặc trưng\n(30 đặc trưng duy nhất)",
                "ArffBuilder\nWeka Instances",
                "Models\nBaseline / URL-only\n/ Hybrid",
                "Reports\nCSV + JSON +\nmanifest"};
        Color[] cols = {MUTED, TEAL, NAVY2, ORANGE, new Color(0x8E6FA8), GREEN};
        double x = 28;
        double bw = 142;
        for (int i = 0; i < steps.length; i++) {
            box(s, x, 120, bw, 96, cols[i], WHITE, steps[i]);
            if (i < steps.length - 1) {
                arrow(s, x + bw + 2, 156, 12, 22, MUTED);
            }
            x += bw + 16;
        }

        card(s, 40, 250, 880, 96, WHITE);
        text(s, 60, 264, 840, 22, 11, true, RED, TextAlign.LEFT, "ZERO-EXECUTION / OFFLINE");
        text(s, 60, 290, 840, 46, 12, false, TEXT, TextAlign.LEFT,
                "Không chạy macro, không mở URL, không resolve DNS, không gọi API. Chỉ đọc byte và phân tích tĩnh.\n"
                        + "Mọi truy cập file đều qua SafeIoUtil (giới hạn kích thước, chuẩn hoá đường dẫn).");

        double[] nums = {505, 5, 250, 250, 1.714, 54};
        String[] labels = {"tệp .eml", "trùng lặp", "benign", "phishing", "ms/email", "MB heap"};
        double cx = 40;
        for (int i = 0; i < nums.length; i++) {
            card(s, cx, 374, 132, 100, LIGHT);
            text(s, cx, 388, 132, 34, 22, true, NAVY, TextAlign.CENTER, fmtNum(nums[i]));
            text(s, cx, 428, 132, 30, 10, false, MUTED, TextAlign.CENTER, labels[i]);
            cx += 146;
        }
    }

    private static void dataProvenanceSlide(XMLSlideShow ppt) {
        XSLFSlide s = contentSlide(ppt, "4. DỮ LIỆU & PROVENANCE",
                "505 tệp .eml, manifest SHA-256, nhãn do generator kiểm soát");
        String[] headers = {"Nguồn", "Vai trò", "Số mẫu", "Ghi chú"};
        String[][] rows = {
                {"Synthetic benign", "Enron-style lành tính", "250", "sinh có kiểm soát"},
                {"Synthetic phishing", "5 họ phishing", "250", "classic / attachment-only / url-only / free-hosting / auth-only"},
                {"Enron thật (hook)", "mở rộng benign", "tuỳ chọn", "config.enronDir"},
                {"Public phishing (hook)", "mở rộng phishing", "tuỳ chọn", "config.phishingDir"}};
        table(s, 40, 112, 880, headers, rows,
                new double[]{190, 190, 80, 420}, 10.5, 11.5, new int[]{0});

        String[][] stats = {
                {"505", "tệp .eml"}, {"5", "trùng lặp"}, {"250/250", "cân bằng"},
                {"100%", "coverage"}, {"SHA-256", "manifest"}, {"1.714 ms", "mỗi email"}};
        double x = 40;
        for (String[] st : stats) {
            card(s, x, 300, 134, 96, WHITE);
            text(s, x, 316, 134, 34, 20, true, TEAL, TextAlign.CENTER, st[0]);
            text(s, x, 354, 134, 28, 10, false, MUTED, TextAlign.CENTER, st[1]);
            x += 148;
        }
    }

    private static void designSlide(XMLSlideShow ppt) {
        XSLFSlide s = contentSlide(ppt, "5. THIẾT KẾ MÔ HÌNH CẢI TIẾN",
                "30 đặc trưng (4 khối mô hình) + luật kết hợp");
        String[][] cards = {
                {"CƠ SỞ", "3", "số từ khóa, số URL, độ dài thân thư"},
                {"TIÊU ĐỀ – XÁC THỰC", "10", "SPF/DKIM/DMARC, From/Return-Path, Received"},
                {"URL BÀI BÁO", "8", "gạch nối, tên miền cấp cao, lưu trữ miễn phí, IP, độ sâu tên miền con"},
                {"TỆP ĐÍNH KÈM + YARA", "10", "sai chữ ký tệp, macro, 8 luật"}};
        Color[] cols = {new Color(0x8FA6B8), TEAL, ORANGE, new Color(0x8E6FA8)};
        double x = 40;
        for (int i = 0; i < cards.length; i++) {
            card(s, x, 116, 208, 176, WHITE);
            rect(s, x, 116, 208, 6, cols[i]);
            text(s, x + 14, 132, 180, 22, 10, true, cols[i], TextAlign.LEFT, cards[i][0]);
            text(s, x + 14, 158, 180, 40, 30, true, NAVY, TextAlign.LEFT, cards[i][1]);
            text(s, x + 14, 204, 180, 76, 10.5, false, TEXT, TextAlign.LEFT, cards[i][2]);
            x += 218;
        }
        text(s, 40, 293, 880, 13, 9.0, false, MUTED, TextAlign.LEFT,
                "31 lượt đặc trưng → 30 duy nhất (url_count dùng chung nhóm cơ sở & URL bài báo)");
        card(s, 40, 312, 880, 74, NAVY);
        text(s, 58, 324, 844, 22, 11, true, new Color(0x9FE3D9), TextAlign.LEFT, "ĐIỂM KẾT HỢP");
        text(s, 58, 350, 844, 28, 17, true, WHITE, TextAlign.LEFT,
                "điểm = 0.65 × RandomForest + 0.35 × điểm luật (RULE_SCALE = 6.0)");
        card(s, 40, 396, 880, 100, LIGHT);
        text(s, 58, 408, 844, 22, 11, true, ORANGE, TextAlign.LEFT, "CHỐNG RÒ RỈ DỮ LIỆU");
        text(s, 58, 432, 844, 60, 11.5, false, TEXT, TextAlign.LEFT,
                "Tách nhóm rời rạc theo tên miền người gửi: tập huấn luyện không thấy tên miền của tập kiểm thử.\n"
                        + "F1 không rò rỉ: cơ sở 0.41 / chỉ URL 0.41 / đề xuất 1.00.\n"
                        + "Độ đặc hiệu trên tập giữ lại: cơ sở 0.00 / chỉ URL 0.00 / đề xuất 1.00.");
        notes(ppt, s, "4 khối đặc trưng: Baseline 3, Header-Auth 10, Paper-URL 8, Attachment+YARA 10.\n"
                + "31 lượt đặc trưng, 30 unique do url_count dùng chung Baseline & Paper-URL.\n"
                + "Hybrid: 0.65 × RandomForest + 0.35 × rule score (RULE_SCALE = 6.0).\n"
                + "Group-disjoint holdout: F1 0.41/0.41/1.00; Specificity 0.00/0.00/1.00.");
    }

    private static void modelTableSlide(XMLSlideShow ppt, MR[] models) {
        XSLFSlide s = contentSlide(ppt, "5. KẾT QUẢ 3 MÔ HÌNH",
                "5-fold CV × seeds 42/100/2026; F1 trình bày mean ± SD");
        String[] headers = {"Mô hình", "Đặc trưng", "Chính xác", "Bao phủ", "F1 (trung bình ± ĐLC)", "F1 trung bình", "ROC-AUC", "PR-AUC", "Báo nhầm", "Đặc hiệu"};
        String[][] rows = new String[models.length][];
        for (int i = 0; i < models.length; i++) {
            MR m = models[i];
            rows[i] = new String[]{m.shortName, String.valueOf(m.features), f3(m.p), f3(m.r),
                    f3(m.f1) + " ± " + f3(m.f1sd), f3(m.macro), f3(m.roc), f3(m.pr), f3(m.fpr), f3(m.spec)};
        }
        table(s, 30, 112, 900, headers, rows,
                new double[]{190, 46, 74, 68, 132, 70, 74, 72, 56, 58}, 10.0, 10.5,
                new int[]{0});

        card(s, 40, 260, 280, 86, WHITE);
        text(s, 54, 272, 252, 20, 10, true, MUTED, TextAlign.LEFT, "CƠ SỞ");
        text(s, 54, 294, 252, 40, 20, true, NAVY, TextAlign.LEFT, "F1 " + f3(models[0].f1));
        card(s, 330, 260, 280, 86, WHITE);
        text(s, 344, 272, 252, 20, 10, true, MUTED, TextAlign.LEFT, "PAPER URL-ONLY");
        text(s, 344, 294, 252, 40, 20, true, ORANGE, TextAlign.LEFT, "F1 " + f3(models[1].f1));
        card(s, 620, 260, 300, 86, NAVY);
        text(s, 634, 272, 272, 20, 10, true, new Color(0x9FE3D9), TextAlign.LEFT, "PROPOSED HYBRID");
        text(s, 634, 294, 272, 40, 20, true, WHITE, TextAlign.LEFT, "F1 " + f3(models[2].f1));

        text(s, 40, 366, 880, 24, 11, false, TEXT, TextAlign.LEFT,
                "Per-seed F1: Baseline 0.723 / 0.748 / 0.739   ·   Paper 0.750 / 0.750 / 0.750   ·   Proposed 1.000 / 0.996 / 0.996");
        text(s, 40, 392, 880, 24, 11, false, TEXT, TextAlign.LEFT,
                "95% CI của F1: Baseline ±0.0144   ·   Paper 0.0000   ·   Proposed ±0.0026");
        card(s, 40, 428, 880, 72, LIGHT);
        text(s, 58, 440, 844, 48, 12, false, TEXT, TextAlign.LEFT,
                "Delta Proposed vs Baseline: F1 +0.2609 tuyệt đối (+35% tương đối), ROC-AUC +0.2396, PR-AUC +0.1992.\n"
                        + "Recall của Paper URL-only chỉ 0.60 → bỏ sót các email phishing không có URL / chỉ có attachment.");
    }

    private static void confusionSlide(XMLSlideShow ppt, MR[] models, Confusion[] cms) {
        XSLFSlide s = contentSlide(ppt, "5. CONFUSION MATRIX & SECURITY METRICS",
                "Seed 42, positive = phishing; TP/TN/FP/FN là số đếm");
        String[] headers = {"Mô hình", "Dương đúng", "Âm đúng", "Dương sai", "Âm sai", "Độ đặc hiệu", "MCC"};
        String[][] rows = new String[models.length][];
        for (int i = 0; i < models.length; i++) {
            Confusion c = cms[i];
            rows[i] = new String[]{models[i].shortName, String.valueOf(c.tp), String.valueOf(c.tn),
                    String.valueOf(c.fp), String.valueOf(c.fn), f3(c.spec), f3(c.mcc)};
        }
        table(s, 40, 108, 880, headers, rows,
                new double[]{280, 80, 80, 80, 80, 150, 130}, 11.0, 12.0, new int[]{0});

        card(s, 40, 268, 430, 92, WHITE);
        text(s, 56, 280, 398, 20, 10, true, ORANGE, TextAlign.LEFT, "SPECIFICITY – BÁO ĐỘNG SAI");
        text(s, 56, 302, 398, 50, 11.5, false, TEXT, TextAlign.LEFT,
                "Baseline 0.60 → báo động sai 100/250 email lành (40%).\n"
                        + "Proposed 1.00 → không còn false positive trên seed 42.");
        card(s, 490, 268, 430, 92, WHITE);
        text(s, 506, 280, 398, 20, 10, true, NAVY, TextAlign.LEFT, "MCC – CHỈ SỐ BỔ TRỢ");
        text(s, 506, 302, 398, 50, 11.5, false, TEXT, TextAlign.LEFT,
                "Baseline 0.40 · Paper URL-only 0.65 · Proposed 1.00.\n"
                        + "MCC phù hợp khi dữ liệu mất cân bằng (benign >> phishing).");

        text(s, 40, 378, 880, 60, 12, false, TEXT, TextAlign.LEFT,
                "Thông điệp: Paper URL-only có Precision 1.00 nhưng Recall 0.60 (FN=100) – nó bỏ sót mọi email phishing không có URL.\n"
                        + "Proposed Hybrid giảm FN về 0, giữ FP = 0, đồng thời MCC 1.00 → cải thiện cả hai phía.");
    }

    private static void chartsSlide(XMLSlideShow ppt, Path figModels, Path figAblation) throws Exception {
        XSLFSlide s = contentSlide(ppt, "5. TRỰC QUAN HOÁ KẾT QUẢ",
                "So sánh mô hình và ablation study");
        picture(s, figModels, 36, 108, 430, 241);
        picture(s, figAblation, 494, 108, 430, 241);
        text(s, 36, 354, 430, 22, 10.5, false, MUTED, TextAlign.LEFT,
                "Hình 1. So sánh 3 mô hình trên 4 chỉ số chính.");
        text(s, 494, 354, 430, 22, 10.5, false, MUTED, TextAlign.LEFT,
                "Hình 2. Ablation: bỏ Header-Auth làm F1 giảm mạnh nhất.");
        card(s, 36, 396, 888, 100, LIGHT);
        text(s, 54, 408, 860, 76, 11.5, false, TEXT, TextAlign.LEFT,
                "Đọc hình: Paper URL-only giữ Precision/Specificity tuyệt đối nhưng Recall thấp → chỉ bắt được email có URL rõ ràng.\n"
                        + "Proposed Hybrid đẩy cả F1, ROC-AUC, PR-AUC và Specificity lên 1.00 (mean). Ablation xác nhận Header-Auth là nhóm\n"
                        + "đặc trưng quan trọng nhất: bỏ đi làm Recall tụt từ 0.9947 xuống 0.896 và MCC từ 0.9947 xuống 0.9009.");
    }

    private static void ablationSlide(XMLSlideShow ppt, String[] names, double[] recall, double[] f1, double[] mcc) {
        XSLFSlide s = contentSlide(ppt, "5. ABLATION STUDY",
                "Loại bỏ từng nhóm đặc trưng để đo đóng góp");
        String[] headers = {"Cấu hình", "Bao phủ", "F1", "MCC", "Nhận xét"};
        String[] notes = {"tham chiếu", "giảm mạnh nhất", "không đổi", "giảm nhẹ"};
        String[][] rows = new String[names.length][];
        for (int i = 0; i < names.length; i++) {
            rows[i] = new String[]{names[i], f3(recall[i]), f3(f1[i]), f3(mcc[i]), notes[i]};
        }
        table(s, 40, 112, 880, headers, rows,
                new double[]{220, 110, 110, 110, 330}, 12.0, 13.0, new int[]{0, 4});

        card(s, 40, 300, 880, 110, WHITE);
        text(s, 58, 314, 844, 22, 11, true, ORANGE, TextAlign.LEFT, "KẾT LUẬN ABLATION");
        text(s, 58, 340, 844, 60, 12, false, TEXT, TextAlign.LEFT,
                "Bỏ Header-Auth: F1 0.9973 → 0.9451, Recall 0.9947 → 0.8960, MCC 0.9947 → 0.9009.\n"
                        + "Bỏ Paper-URL: gần như không đổi trên corpus này. Bỏ Attachment-YARA: F1 0.9960, MCC 0.9920.\n"
                        + "→ Xác thực email (SPF/DKIM/DMARC + mismatch) là nhóm đặc trưng then chốt của mô hình.");
    }

    private static void errorCostSlide(XMLSlideShow ppt, Path figTld) throws Exception {
        XSLFSlide s = contentSlide(ppt, "8. PHÂN TÍCH LỖI & CHI PHÍ",
                "Dương tính giả / âm tính giả thực tế + chi phí tính toán");
        picture(s, figTld, 36, 104, 390, 218);
        text(s, 36, 326, 390, 22, 10, false, MUTED, TextAlign.LEFT,
                "Hình 3. Tên miền cấp cao bị lạm dụng trong tập dữ liệu ngoại tuyến.");
        bullets(s, 452, 108, 470, new String[]{
                "Âm tính giả trên tập giữ lại theo nhóm:",
                "  thư chỉ có tệp đính kèm, không URL rõ ràng",
                "  → mô hình cơ sở/chỉ URL bỏ sót (độ bao phủ 0.60)",
                "  → mô hình đề xuất bắt được nhờ chữ ký tệp + luật",
                "Dương tính giả: mô hình cơ sở có tỷ lệ báo nhầm 0.40",
                "  → 100/250 thư lành bị báo động sai.",
                "",
                "Chi phí (đo trên máy trình diễn):",
                "  1.714 ms/thư  ·  857 ms cho 500 thư",
                "  ~54 MB bộ nhớ  ·  không gọi mạng, không truy cập mạng"}, 11.5);
        notes(ppt, s, "Top TLD trong corpus: .com 15, .dev 6, .top 4, .app 4, .xyz 3...\n"
                + "FN: email chỉ có attachment / không URL rõ ràng — URL-only bỏ sót.\n"
                + "FP: Baseline FPR 0.40 → 100/250 benign bị báo động sai.\n"
                + "Chi phí: 1.714 ms/email, 857 ms/500 email, ~54 MB heap; không gọi mạng.");
    }

    private static void conclusionSlide(XMLSlideShow ppt, MR[] models) {
        XSLFSlide s = contentSlide(ppt, "9. KẾT LUẬN & HƯỚNG PHÁT TRIỂN",
                "Bài báo – Tái lập – Cải tiến và các bước tiếp theo");
        String[] headers = {"Hạng mục", "Bài báo", "Tái lập ngoại tuyến", "Cải tiến"};
        String[][] rows = {
                {"Đơn vị phân tích", "15.126 tên miền mới", n0(250) + " thư / 37 máy chủ", "30 đặc trưng / thư"},
                {"TLS hợp lệ", "79.3%", "100% (thay thế)", "-"},
                {"Lưu trữ miễn phí", "Firebase web.app", "32.43%", "-"},
                {"Tên miền con", "API so với báo cáo minh bạch", "60% thư", "-"},
                {"F1 mô hình", "-", "chỉ URL 0.750", f3(models[2].f1) + " (kết hợp)"},
                {"Độ đặc hiệu", "-", "cơ sở 0.60", "1.00"},
                {"MCC", "-", "chỉ URL 0.65", "1.00"}};
        table(s, 36, 104, 560, headers, rows,
                new double[]{140, 110, 180, 130}, 9.5, 10.0, new int[]{0});

        text(s, 618, 104, 306, 24, 11, true, ORANGE, TextAlign.LEFT, "HƯỚNG PHÁT TRIỂN");
        bullets(s, 618, 136, 306, new String[]{
                "Bổ sung tập dữ liệu Enron thật +",
                "  thư lừa đảo .eml công khai",
                "Thêm đường cong ROC/PR + khoảng tin cậy bootstrap",
                "Mở rộng luật YARA (macro, OLE)",
                "Đóng gói dòng lệnh + Docker ngoại tuyến",
                "Thử nghiệm trên dữ liệu mất cân bằng",
                "  (thư lành >> thư lừa đảo)"}, 10.5);

        card(s, 36, 344, 888, 156, NAVY);
        text(s, 54, 358, 852, 24, 11, true, new Color(0x9FE3D9), TextAlign.LEFT, "3 THÔNG ĐIỆP CHÍNH");
        text(s, 54, 386, 852, 104, 12, false, new Color(0xD5E1EC), TextAlign.LEFT,
                "1. Tái lập trung thực: nói rõ phần suy ra được ngoại tuyến và phần không có do dữ liệu đóng / truy vấn trực tuyến.\n"
                        + "2. Cải tiến có kiểm chứng: 30 đặc trưng + luật kết hợp đạt F1 0.9973, độ đặc hiệu 1.00, MCC 0.9947 (trung bình).\n"
                        + "3. Chống rò rỉ: tập giữ lại rời rạc theo nhóm 0.41 so với 1.00 chứng minh cải tiến không phải do rò rỉ tên miền.");
        notes(ppt, s, "Kết luận: tái lập trung thực phần suy ra được offline, ghi rõ n/a cho phần cần truy vấn online.\n"
                + "Cải tiến: F1 0.9973, Specificity 1.00, MCC 0.9947 (mean 3 seed).\n"
                + "Group-disjoint holdout 0.41 vs 1.00 chứng minh không rò rỉ domain.\n"
                + "Hướng phát triển: Enron thật + phishing công khai, ROC/PR curve, YARA mở rộng, CLI/Docker.");
    }

    private static void referencesSlide(XMLSlideShow ppt) {
        XSLFSlide s = newSlide(ppt, BG);
        rect(s, 0, 0, W, 8, TEAL);
        text(s, 64, 70, 820, 40, 13, true, TEAL, TextAlign.LEFT, "THAM KHẢO & LỜI CẢM ƠN");
        text(s, 64, 128, 840, 60, 32, true, NAVY, TextAlign.LEFT, "Cảm ơn thầy và các bạn đã theo dõi!");
        text(s, 64, 210, 840, 26, 14, false, MUTED, TextAlign.LEFT,
                "Hỏi & đáp: sẵn sàng trình diễn trực tiếp quy trình và chạy lại toàn bộ kết quả.");
        card(s, 64, 268, 832, 168, WHITE);
        text(s, 84, 276, 792, 22, 11, true, ORANGE, TextAlign.LEFT, "TÀI LIỆU");
        bullets(s, 84, 304, 792, new String[]{
                "Agarwal & Vasek (2026). Examining newly registered phishing domains at scale.",
                "  Dịch: Nghiên cứu quy mô lớn các tên miền lừa đảo mới đăng ký.",
                "DOI 10.1093/cybsec/tyag020 – bài báo gốc.",
                "Weka 3.8.6 – thư viện học máy (NaiveBayes, RandomForest).",
                "Enron Email Dataset (CMU) – nguồn benign tham khảo."}, 10.5, TEXT);
        text(s, 64, 452, 832, 22, 11, false, MUTED, TextAlign.LEFT,
                "Đề tài 124 · Báo cáo giữa kỳ · 11/2026 · Bùi Ngọc Huy");
    }

    // ------------------------------------------------------------------ 11-slide deck

    private static void notes(XMLSlideShow ppt, XSLFSlide slide, String text) {
        XSLFNotes notes = ppt.getNotesSlide(slide);
        XSLFTextBox box = notes.createTextBox();
        box.setAnchor(new Rectangle2D.Double(20, 20, 700, 470));
        box.clearText();
        for (String line : text.split("\n", -1)) {
            XSLFTextParagraph p = box.addNewTextParagraph();
            p.setTextAlign(TextAlign.LEFT);
            p.setSpaceAfter(2.0);
            p.setSpaceBefore(0.0);
            p.setLineSpacing(100.0);
            XSLFTextRun run = p.addNewTextRun();
            run.setText(line);
            run.setFontFamily(FONT);
            run.setFontSize(10.5);
            run.setFontColor(TEXT);
        }
    }

    private static void s01Title(XMLSlideShow ppt) {
        XSLFSlide s = newSlide(ppt, BG);
        rect(s, 0, 0, W, 8, TEAL);
        rect(s, 0, H - 8, W, 8, TEAL);
        rect(s, 64, 112, 6, 210, ORANGE);
        text(s, 88, 108, 800, 30, 13, true, TEAL, TextAlign.LEFT,
                "BÁO CÁO GIỮA KỲ  ·  ĐỀ TÀI 124  ·  JAVA / MAVEN");
        text(s, 88, 150, 850, 110, 32, true, NAVY, TextAlign.LEFT,
                "Phân tích thư điện tử lừa đảo ngoại tuyến\ntừ tệp\u00A0.eml");
        text(s, 88, 268, 800, 60, 18, false, MUTED, TextAlign.LEFT,
                "Tái lập kết quả bài báo tyag020 (CH1–CH4) + mô hình học máy cải tiến");
        chip(s, 88, 340, 190, 34, new Color(0xE3F1F2), "100% ngoại tuyến", TEAL);
        chip(s, 290, 340, 210, 34, new Color(0xE3F1F2), "Không thực thi mã", TEAL);
        chip(s, 512, 340, 230, 34, new Color(0xE3F1F2), "38/38 bài kiểm thử đạt", TEAL);
        text(s, 88, 410, 760, 22, 13, false, MUTED, TextAlign.LEFT,
                "Sinh viên: Bùi Ngọc Huy   |   GVHD: Phạm Trọng Huynh   |   11/2026");
        text(s, 88, 438, 760, 22, 11, false, MUTED, TextAlign.LEFT,
                "DOI 10.1093/cybsec/tyag020  ·  thư viện Weka 3.8.6  ·  JDK 21 (Temurin)");
        text(s, 88, 502, 860, 16, 8.5, false, MUTED, TextAlign.LEFT,
                "1 Tổng quan · 2 Phương pháp · 3 Tái lập câu hỏi · 4 Kiến trúc · 5 Mô hình · 6 Kết quả · 7 Loại bỏ đặc trưng · 8 Lỗi/chi phí · 9 Kết luận");
        notes(ppt, s, "Mở đầu: đề tài 124 – phân tích email phishing offline từ .eml.\n"
                + "Mục tiêu: tái lập paper tyag020 (RQ1–RQ4) và xây mô hình ML cải tiến vượt baseline.\n"
                + "Toàn bộ pipeline chạy offline, zero-execution; 38/38 test pass.");
    }

    private static void s02Overview(XMLSlideShow ppt, Path paper) throws Exception {
        XSLFSlide s = contentSlide(ppt, "1. TỔNG QUAN BÀI BÁO & MỤC TIÊU",
                "Examining newly registered phishing domains at scale");
        picture(s, paper.resolve("paper_page1.png"), 40, 104, 360, 253);
        text(s, 40, 362, 360, 30, 9, false, MUTED, TextAlign.LEFT,
                "Trang 1 bài báo gốc — Agarwal & Vasek (2026)\nDOI 10.1093/cybsec/tyag020");

        text(s, 426, 104, 494, 40, 13, false, TEXT, TextAlign.LEFT,
                "Dịch: Nghiên cứu quy mô lớn các tên miền lừa đảo mới đăng ký");
        text(s, 426, 146, 494, 20, 11, false, MUTED, TextAlign.LEFT,
                "Agarwal & Vasek (2026) · 15.126 tên miền mới / 11 tháng");
        text(s, 426, 168, 494, 20, 11, false, MUTED, TextAlign.LEFT,
                "Nguồn: WHOIS, DNS thụ động, nhật ký CT, ASN, GSB");
        text(s, 426, 198, 494, 20, 11, true, ORANGE, TextAlign.LEFT, "MỤC TIÊU ĐỀ TÀI 124");
        bullets(s, 426, 222, 494, new String[]{
                "Tái lập CH1–CH4 trên phần dữ liệu suy ra từ .eml",
                "Dựng lại mô hình dữ liệu 20+ trường",
                "Quy trình Java ngoại tuyến, không thực thi mã",
                "Cải tiến học máy vượt mô hình cơ sở (mục tiêu 9–10)"}, 11.5);

        double x = 48;
        String[][] rqs = {
                {"CH1", "Hạ tầng, TLS, Cloudflare, lưu trữ miễn phí"},
                {"CH2", "Thương hiệu, gạch nối, tên miền cấp cao bị lạm dụng"},
                {"CH3", "Vòng đời tên miền, tái đăng ký"},
                {"CH4", "Điểm mù tên miền con của GSB"}};
        for (String[] rq : rqs) {
            card(s, x, 420, 208, 74, NAVY);
            text(s, x + 12, 430, 60, 20, 11, true, new Color(0xFFC58A), TextAlign.LEFT, rq[0]);
            text(s, x + 12, 452, 184, 34, 10, false, new Color(0xD5E1EC), TextAlign.LEFT, rq[1]);
            x += 218;
        }
        notes(ppt, s, "Paper gốc: Agarwal & Vasek (2026), Journal of Cybersecurity.\n"
                + "Dịch tiêu đề: Nghiên cứu quy mô lớn các tên miền phishing mới đăng ký.\n"
                + "Dữ liệu: 15.126 domain NRD trong 11 tháng; làm giàu bằng WHOIS, Passive DNS, CT logs, ASN, GSB.\n"
                + "4 RQ: hạ tầng/TLS, thương hiệu, vòng đời/tái đăng ký, hiệu quả GSB.");
    }

    private static void s03MethodData(XMLSlideShow ppt, Path paper) throws Exception {
        XSLFSlide s = contentSlide(ppt, "2. PHƯƠNG PHÁP & MÔ HÌNH DỮ LIỆU",
                "Bài báo làm giàu dữ liệu từ 5 nguồn; đề tài dựng lại 20+ trường");
        picture(s, paper.resolve("figure1_compromised.png"), 40, 104, 400, 157);
        text(s, 40, 263, 400, 16, 9, false, MUTED, TextAlign.LEFT,
                "Hình 1 — website hợp pháp bị chiếm quyền (bài báo)");
        picture(s, paper.resolve("figure2_nrd.png"), 480, 104, 440, 124);
        text(s, 480, 230, 440, 16, 9, false, MUTED, TextAlign.LEFT,
                "Hình 2 — tên miền mới: nhà đăng ký → AS → chứng chỉ TLS (bài báo)");

        String[] headers = {"Nhóm trường", "Ví dụ", "Phục vụ"};
        String[][] rows = {
                {"Định danh & nguồn", "domain, first_seen, source, label", "Tất cả"},
                {"Hạ tầng", "tls_valid, cert_count, asn, cloudflare", "CH1"},
                {"Thương hiệu", "brand, hyphen, tld, free_hosting", "CH2"},
                {"Vòng đời", "whois_age, reregistered, dns_active_days", "CH3"},
                {"Phân giải", "subdomain_depth, gsb_listed, browser_verdict", "CH4"},
                {"Nội dung thư", "subject/body keyword, url_count, auth results", "Tất cả"}};
        table(s, 40, 290, 880, headers, rows,
                new double[]{190, 600, 90}, 10.5, 11.0, new int[]{0});
        text(s, 40, 482, 880, 14, 8.5, false, MUTED, TextAlign.LEFT,
                "Tên trường giữ nguyên theo mã nguồn Java.");
        notes(ppt, s, "Paper mô tả 4 bước: thu thập NRD → làm giàu WHOIS/Passive DNS/CT/ASN/GSB → phân tích → trả lời RQ.\n"
                + "Figure 1: kẻ tấn công chiếm website hợp pháp. Figure 2: mua domain mới (NRD), thuê AS và xin TLS CA.\n"
                + "Đề tài dựng lại schema PaperDomainRecord 20+ trường để mọi trường đo được offline.\n"
                + "Các trường cần truy vấn online (WHOIS lịch sử, CT logs, GSB) được đánh dấu n/a thay vì suy diễn.");
    }

    private static void s04Replication(XMLSlideShow ppt, Path paper, double hosts, double emails,
                                       double https, double freeHost, double brandHyphen, double subdomain) throws Exception {
        XSLFSlide s = contentSlide(ppt, "3. TÁI LẬP CÂU HỎI NGHIÊN CỨU 1–4",
                "Bài báo và tái lập ngoại tuyến — phần suy ra được từ .eml");
        String[] headers = {"CH", "Chỉ số", "Bài báo tyag020", "Đề tài 124 (ngoại tuyến)", "Đánh giá"};
        String[][] rows = {
                {"CH1", "Tên miền mới lừa đảo", "15.126 (11 tháng)", n0(emails) + " thư / " + n0(hosts) + " máy chủ", "thay thế"},
                {"CH1", "TLS hợp lệ", "79.3%", pct(https) + " (URL HTTPS thay thế)", "khác định nghĩa"},
                {"CH1", "Tên miền > 50 chứng chỉ", "190", "không có (thiếu nhật ký CT)", "không có"},
                {"CH1", "Cloudflare (ủy nhiệm)", "26%", "không có (thiếu DNS/ASN)", "không có"},
                {"CH1", "Lạm dụng lưu trữ miễn phí", "Firebase web.app", pct(freeHost), "tái lập"},
                {"CH2", "Thương hiệu + gạch nối", "pay-ap[.]com", pct(brandHyphen), "tái lập"},
                {"CH3", "Vòng đời TB", "8.6 ngày (so với 21 giờ)", "không có (cần DNS theo thời gian)", "không có"},
                {"CH3", "Tái đăng ký", "25.7%", "không có (cần WHOIS lịch sử)", "không có"},
                {"CH4", "Điểm mù tên miền con", "API so với báo cáo minh bạch", pct(subdomain) + " thư", "thay thế"}};
        table(s, 36, 96, 888, headers, rows,
                new double[]{46, 160, 150, 310, 150}, 9.5, 10.0, new int[]{0});

        text(s, 36, 366, 500, 44, 10, false, TEXT, TextAlign.LEFT,
                "505 tệp .eml · 5 trùng lặp · 250/250 · độ phủ 100%\nBản kê SHA-256 · 1.714 ms/thư · 54 MB bộ nhớ");
        picture(s, paper.resolve("table12_gsb.png"), 540, 386, 380, 92);
        text(s, 540, 486, 380, 16, 8.5, false, MUTED, TextAlign.LEFT,
                "Bảng 12 — Phát hiện của GSB (Agarwal & Vasek, 2026)");
        notes(ppt, s, "Bảng đối chiếu: chỉ số nào suy ra được từ .eml thì báo cáo; chỉ số cần WHOIS/CT/GSB online thì ghi n/a.\n"
                + "n/a gồm: >50 cert, Cloudflare proxy, vòng đời, tái đăng ký — cần truy vấn online.\n"
                + "Tái lập được: free-hosting 32.43%, brand+hyphen 54.05%, subdomain 60%.\n"
                + "Table 12 (paper): GSB API phát hiện 3.012/15.126 domain; transparency report 3.981; VirusTotal 2.508.");
    }

    private static void s05LimitsArchitecture(XMLSlideShow ppt) {
        XSLFSlide s = contentSlide(ppt, "4. TỪ HẠN CHẾ BÀI BÁO ĐẾN KIẾN TRÚC JAVA NGOẠI TUYẾN",
                "5 hạn chế → 4 mô-đun; quy trình 100% ngoại tuyến, không thực thi mã");
        String[] headers = {"Hạn chế", "Giải pháp", "Mô-đun"};
        String[][] rows = {
                {"Phụ thuộc WHOIS/DNS/CT trực tuyến", "Trích xuất tĩnh từ .eml", "OfflineEmlParser, UrlDomainAnalyzer"},
                {"Bỏ qua nội dung thư", "Từ khóa tiêu đề/thân thư", "EmailFeatureExtractor"},
                {"Không kiểm tra xác thực", "SPF/DKIM/DMARC + sai lệch", "AuthHeaderValidator"},
                {"Không phân tích tệp đính kèm", "Chữ ký tệp, macro, YARA", "SafeAttachmentInspector, RuleEngine"},
                {"Không có mô hình học máy", "30 đặc trưng + Weka", "FeatureSchema, HybridScoringClassifier"}};
        table(s, 36, 104, 470, headers, rows,
                new double[]{150, 170, 150}, 9.5, 9.5, new int[]{0});

        text(s, 36, 296, 470, 20, 11, true, TEAL, TextAlign.LEFT, "KẾT QUẢ");
        text(s, 36, 320, 470, 70, 10.5, false, TEXT, TextAlign.LEFT,
                "44 tệp Java chính + 10 tệp kiểm thử · 38/38 đạt\n"
                        + "Không chạy macro · không mở URL · không DNS\n"
                        + "giới hạn 5 MB · chặn duyệt đường dẫn");

        String[] steps = {
                "1 · Đầu vào: tệp .eml",
                "2 · Bộ phân tích: giới hạn 5 MB, chặn đường dẫn",
                "3 · Bộ trích xuất: 5 họ → 30 đặc trưng",
                "4 · Tạo ARFF → dữ liệu Weka",
                "5 · Mô hình: cơ sở / URL bài báo / kết hợp",
                "6 · Báo cáo: CSV + JSON + bản kê"};
        Color[] stepColors = {MUTED, TEAL, NAVY2, ORANGE, new Color(0x8E6FA8), GREEN};
        double y = 104;
        for (int i = 0; i < steps.length; i++) {
            box(s, 530, y, 390, 40, stepColors[i], WHITE, steps[i]);
            y += 52;
        }
        text(s, 530, 420, 390, 20, 10.5, false, MUTED, TextAlign.LEFT,
                "Không mạng · không thực thi mã · giới hạn 5 MB · 38/38 bài kiểm thử");
        notes(ppt, s, "5 hạn chế → 4 module: parser/extractor, auth, attachment/YARA, ML.\n"
                + "Pipeline: .eml → parser → 5 họ đặc trưng (30 unique) → ARFF → Weka → báo cáo.\n"
                + "An toàn: 5 MB cap, magic bytes, path traversal, không resolve DNS, không mở URL.\n"
                + "44 tệp Java chính, 10 tệp test, 38/38 pass.");
    }

    private static void s07Results(XMLSlideShow ppt, MR[] models, Confusion[] cms) {
        XSLFSlide s = contentSlide(ppt, "6. KẾT QUẢ & CHỈ SỐ AN NINH",
                "Mô hình kết hợp vượt cơ sở: F1 0.997 · độ đặc hiệu 1.00 · MCC 0.995");
        String[] headers = {"Mô hình", "Đặc trưng", "Chính xác", "Bao phủ", "F1 (trung bình ± ĐLC)",
                "F1 trung bình", "ROC-AUC", "PR-AUC", "Báo nhầm", "Đặc hiệu"};
        String[][] rows = new String[models.length][];
        for (int i = 0; i < models.length; i++) {
            MR m = models[i];
            rows[i] = new String[]{m.shortName, String.valueOf(m.features), f3(m.p), f3(m.r),
                    f3(m.f1) + " ± " + f3(m.f1sd), f3(m.macro), f3(m.roc), f3(m.pr),
                    f3(m.fpr), f3(m.spec)};
        }
        table(s, 30, 100, 900, headers, rows,
                new double[]{190, 58, 74, 68, 120, 70, 74, 72, 56, 58}, 9.5, 10.0, new int[]{0});

        String[] cHeaders = {"Mô hình", "Dương đúng", "Âm đúng", "Dương sai", "Âm sai", "Độ đặc hiệu", "MCC"};
        String[][] cRows = new String[models.length][];
        for (int i = 0; i < models.length; i++) {
            Confusion c = cms[i];
            cRows[i] = new String[]{models[i].shortName, String.valueOf(c.tp), String.valueOf(c.tn),
                    String.valueOf(c.fp), String.valueOf(c.fn), f3(c.spec), f3(c.mcc)};
        }
        table(s, 40, 220, 880, cHeaders, cRows,
                new double[]{280, 80, 80, 80, 80, 150, 130}, 10.5, 11.5, new int[]{0});

        text(s, 40, 336, 880, 20, 11, false, TEXT, TextAlign.LEFT,
                "Chênh lệch so với mô hình cơ sở: F1 +0.2609 · ROC-AUC +0.2396 · PR-AUC +0.1992");
        text(s, 40, 358, 880, 20, 10, false, MUTED, TextAlign.LEFT,
                "F1 theo từng lần chạy: cơ sở 0.723/0.748/0.739 · chỉ URL 0.750/0.750/0.750 · đề xuất 1.000/0.996/0.996 · khoảng tin cậy 95% ±0.0026");
        text(s, 40, 382, 880, 20, 10.5, false, TEXT, TextAlign.LEFT,
                "Chỉ dùng URL: độ chính xác 1.00 nhưng độ bao phủ 0.60 → bỏ sót thư lừa đảo không có URL.");
        notes(ppt, s, "Bảng trên: mean qua 5-fold CV × 3 seed (42/100/2026).\n"
                + "F1: Baseline 0.7364 ± 0.0127; Paper URL-only 0.7500; Proposed 0.9973 ± 0.0023 (95% CI ±0.0026).\n"
                + "Confusion seed 42: Baseline 198/150/100/52; Paper 150/250/0/100; Proposed 250/250/0/0.\n"
                + "Specificity 0.60 → 1.00 (giảm báo động sai); MCC 0.399 → 0.655 → 1.000.");
    }

    private static void s08ChartsAblation(XMLSlideShow ppt, Path figModels, Path figAblation,
                                          String[] names, double[] recall, double[] f1, double[] mcc)
            throws Exception {
        XSLFSlide s = contentSlide(ppt, "7. TRỰC QUAN HOÁ & LOẠI BỎ ĐẶC TRƯNG",
                "Bỏ nhóm xác thực tiêu đề làm F1 giảm mạnh nhất");
        picture(s, figModels, 36, 100, 430, 241);
        picture(s, figAblation, 494, 100, 430, 241);
        text(s, 36, 344, 430, 16, 9, false, MUTED, TextAlign.LEFT,
                "Hình 1 — So sánh 3 mô hình");
        text(s, 494, 344, 430, 16, 9, false, MUTED, TextAlign.LEFT,
                "Hình 2 — Loại bỏ đặc trưng");

        String[] headers = {"Cấu hình", "Bao phủ", "F1", "MCC", "Nhận xét"};
        String[] notes = {"tham chiếu", "giảm mạnh nhất", "không đổi", "giảm nhẹ"};
        String[][] rows = new String[names.length][];
        for (int i = 0; i < names.length; i++) {
            rows[i] = new String[]{shortAblation(names[i]), f3(recall[i]), f3(f1[i]), f3(mcc[i]), notes[i]};
        }
        table(s, 40, 366, 880, headers, rows,
                new double[]{220, 110, 110, 110, 330}, 10.5, 11.0, new int[]{0, 4});
        notes(ppt, s, "Đọc hình: Paper URL-only giữ Precision/Specificity tuyệt đối nhưng Recall 0.60.\n"
                + "Proposed đẩy F1, ROC-AUC, PR-AUC, Specificity lên 1.00 (mean).\n"
                + "Ablation: bỏ Header-Auth → F1 0.9973→0.9451, Recall 0.9947→0.8960, MCC 0.9947→0.9009.\n"
                + "Bỏ Paper-URL gần như không đổi; bỏ Attachment-YARA còn F1 0.9960.");
    }

    private static void s11Closing(XMLSlideShow ppt) {
        XSLFSlide s = newSlide(ppt, BG);
        rect(s, 0, 0, W, 6, TEAL);
        text(s, 44, 30, 780, 20, 10, true, TEAL, TextAlign.LEFT, "THAM KHẢO & LỜI CẢM ƠN");
        text(s, 44, 58, 820, 40, 24, true, NAVY, TextAlign.LEFT,
                "Tổng kết & tài liệu tham khảo");
        line(s, 44, 108, 872, LINE);

        card(s, 40, 130, 560, 310, WHITE);
        text(s, 60, 146, 520, 22, 11, true, ORANGE, TextAlign.LEFT, "TÀI LIỆU");
        bullets(s, 60, 176, 520, new String[]{
                "Agarwal & Vasek (2026). Examining newly registered phishing domains at scale.",
                "  Dịch: Nghiên cứu quy mô lớn các tên miền lừa đảo mới đăng ký.",
                "DOI 10.1093/cybsec/tyag020 — bài báo gốc.",
                "Weka 3.8.6 — thư viện học máy (NaiveBayes, RandomForest).",
                "Bộ dữ liệu Enron (CMU) — nguồn thư lành tham khảo."}, 11.0, TEXT);

        text(s, 630, 150, 290, 130, 22, true, NAVY, TextAlign.LEFT,
                "Cảm ơn thầy và các bạn đã theo dõi!");
        text(s, 630, 300, 290, 70, 11, false, MUTED, TextAlign.LEFT,
                "Hỏi & đáp: sẵn sàng trình diễn trực tiếp quy trình và chạy lại toàn bộ kết quả.");
        text(s, 630, 390, 290, 70, 10, false, MUTED, TextAlign.LEFT,
                "Đề tài 124 · Báo cáo giữa kỳ · 11/2026 · Bùi Ngọc Huy");
        text(s, 800, 512, 116, 18, 8.5, false, MUTED, TextAlign.RIGHT, "11 / 11");
        notes(ppt, s, "Tài liệu: paper gốc + Weka + Enron. Mã nguồn Java 21/Maven; số liệu trong artifacts/reports.\n"
                + "Sẵn sàng demo: chạy lại pipeline, mở artifacts, giải thích từng con số.");
    }

    // ------------------------------------------------------------------ data

    private static final class MR {
        String name;
        String shortName;
        int features;
        double p, r, f1, f1sd, macro, roc, pr, fpr, accuracy, spec, mcc, mccSd;
    }

    private static final class Confusion {
        int tp, tn, fp, fn;
        double spec, mcc;
    }

    private static MR model(Map<String, Map<String, String>> csv, String key, String shortName) {
        Map<String, String> row = csv.get(key);
        if (row == null) {
            throw new IllegalStateException("Missing model row: " + key);
        }
        MR m = new MR();
        m.name = key;
        m.shortName = shortName;
        m.features = (int) d(row, "features");
        m.p = d(row, "precision_mean");
        m.r = d(row, "recall_mean");
        m.f1 = d(row, "f1_mean");
        m.f1sd = d(row, "f1_sd");
        m.macro = d(row, "macro_f1_mean");
        m.roc = d(row, "roc_auc_mean");
        m.pr = d(row, "pr_auc_mean");
        m.fpr = d(row, "fpr_mean");
        m.accuracy = d(row, "accuracy_mean");
        m.spec = d(row, "specificity_mean");
        m.mcc = d(row, "mcc_mean");
        m.mccSd = d(row, "mcc_sd");
        return m;
    }

    private static Confusion confusion(List<Map<String, String>> csv, String model) {
        for (Map<String, String> row : csv) {
            if (model.equals(row.get("model")) && "42".equals(row.get("seed"))) {
                Confusion c = new Confusion();
                c.tp = (int) d(row, "tp");
                c.tn = (int) d(row, "tn");
                c.fp = (int) d(row, "fp");
                c.fn = (int) d(row, "fn");
                c.spec = d(row, "specificity");
                c.mcc = d(row, "mcc");
                return c;
            }
        }
        throw new IllegalStateException("Missing confusion row for " + model);
    }

    private static String shortAblation(String name) {
        if (name.startsWith("w/o Header")) {
            return "Bỏ xác thực tiêu đề";
        }
        if (name.startsWith("w/o Paper")) {
            return "Bỏ URL bài báo";
        }
        if (name.startsWith("w/o Attachment")) {
            return "Bỏ tệp đính kèm + YARA";
        }
        if (name.equals("Full")) {
            return "Đầy đủ";
        }
        return name;
    }

    private static Map<String, Map<String, String>> readCsvMap(Path path) throws IOException {
        Map<String, Map<String, String>> map = new LinkedHashMap<>();
        List<Map<String, String>> list = readCsvList(path);
        for (Map<String, String> row : list) {
            String key = row.getOrDefault("model", row.getOrDefault("metric", ""));
            map.put(key, row);
        }
        return map;
    }

    private static List<Map<String, String>> readCsvList(Path path) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return rows;
        }
        String[] headers = splitCsv(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            String[] values = splitCsv(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.length; j++) {
                row.put(headers[j], j < values.length ? values[j] : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out.toArray(new String[0]);
    }

    private static double dv(Map<String, Map<String, String>> map, String key) {
        Map<String, String> row = map.get(key);
        if (row == null) {
            return 0.0d;
        }
        return d(row, "value");
    }

    private static double d(Map<String, String> row, String key) {
        String value = row == null ? null : row.get(key);
        if (value == null || value.isBlank()) {
            return 0.0d;
        }
        return Double.parseDouble(value.trim());
    }

    private static Path findRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            if (Files.isRegularFile(dir.resolve("artifacts/reports/model_comparison.csv"))) {
                return dir;
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }
        throw new IllegalStateException("Cannot locate repository root (artifacts/reports missing)");
    }

    // ------------------------------------------------------------------ deck primitives

    private static XSLFSlide newSlide(XMLSlideShow ppt, Color background) {
        XSLFSlideLayout layout = ppt.getSlideMasters().get(0)
                .getLayout(SlideLayout.BLANK);
        XSLFSlide slide = ppt.createSlide(layout);
        rect(slide, 0, 0, W, H, background);
        page++;
        return slide;
    }

    private static XSLFSlide contentSlide(XMLSlideShow ppt, String kicker, String subtitle) {
        XSLFSlide s = newSlide(ppt, BG);
        rect(s, 0, 0, W, 6, TEAL);
        text(s, 44, 30, 780, 20, 10, true, TEAL, TextAlign.LEFT, kicker);
        text(s, 44, 50, 820, 38, 22, true, NAVY, TextAlign.LEFT, subtitle);
        line(s, 44, 96, 872, LINE);
        text(s, 44, 512, 700, 18, 8.5, false, MUTED, TextAlign.LEFT,
                "DT124 · Phân tích thư lừa đảo ngoại tuyến · Bùi Ngọc Huy");
        text(s, 800, 512, 116, 18, 8.5, false, MUTED, TextAlign.RIGHT,
                page + " / " + TOTAL);
        return s;
    }

    private static void rect(XSLFSlide slide, double x, double y, double w, double h, Color fill) {
        XSLFAutoShape sh = slide.createAutoShape();
        sh.setShapeType(ShapeType.RECT);
        sh.setAnchor(rect2d(x, y, w, h));
        sh.setFillColor(fill);
        sh.setLineColor(fill);
        sh.setLineWidth(0.5);
    }

    private static void circle(XSLFSlide slide, double x, double y, double d, Color fill) {
        XSLFAutoShape sh = slide.createAutoShape();
        sh.setShapeType(ShapeType.ELLIPSE);
        sh.setAnchor(rect2d(x, y, d, d));
        sh.setFillColor(fill);
        sh.setLineColor(fill);
    }

    private static void card(XSLFSlide slide, double x, double y, double w, double h, Color fill) {
        XSLFAutoShape sh = slide.createAutoShape();
        sh.setShapeType(ShapeType.ROUND_RECT);
        sh.setAnchor(rect2d(x, y, w, h));
        sh.setFillColor(fill);
        sh.setLineColor(darker(fill));
        sh.setLineWidth(0.75);
    }

    private static Color darker(Color color) {
        return new Color(
                (int) Math.round(color.getRed() * 0.88d),
                (int) Math.round(color.getGreen() * 0.88d),
                (int) Math.round(color.getBlue() * 0.88d));
    }

    private static void line(XSLFSlide slide, double x, double y, double w, Color color) {
        XSLFAutoShape sh = slide.createAutoShape();
        sh.setShapeType(ShapeType.RECT);
        sh.setAnchor(rect2d(x, y, w, 1));
        sh.setFillColor(color);
        sh.setLineColor(color);
    }

    private static void arrow(XSLFSlide slide, double x, double y, double w, double h, Color color) {
        XSLFAutoShape sh = slide.createAutoShape();
        sh.setShapeType(ShapeType.RIGHT_ARROW);
        sh.setAnchor(rect2d(x, y, w, h));
        sh.setFillColor(color);
        sh.setLineColor(color);
    }

    private static void box(XSLFSlide slide, double x, double y, double w, double h, Color fill, Color fg, String textValue) {
        XSLFAutoShape sh = slide.createAutoShape();
        sh.setShapeType(ShapeType.ROUND_RECT);
        sh.setAnchor(rect2d(x, y, w, h));
        sh.setFillColor(fill);
        sh.setLineColor(fill);
        sh.setText(textValue);
        sh.setVerticalAlignment(VerticalAlignment.MIDDLE);
        for (XSLFTextParagraph p : sh.getTextParagraphs()) {
            p.setTextAlign(TextAlign.CENTER);
            p.setSpaceAfter(0.0);
            p.setLineSpacing(95.0);
            for (XSLFTextRun run : p.getTextRuns()) {
                run.setFontFamily(FONT);
                run.setFontSize(11.0);
                run.setFontColor(fg);
                run.setBold(false);
            }
        }
    }

    private static void chip(XSLFSlide slide, double x, double y, double w, double h, Color fill, String label, Color fg) {
        XSLFAutoShape sh = slide.createAutoShape();
        sh.setShapeType(ShapeType.ROUND_RECT);
        sh.setAnchor(rect2d(x, y, w, h));
        sh.setFillColor(fill);
        sh.setLineColor(darker(fill));
        sh.setLineWidth(0.75);
        sh.setText(label);
        sh.setVerticalAlignment(VerticalAlignment.MIDDLE);
        for (XSLFTextParagraph p : sh.getTextParagraphs()) {
            p.setTextAlign(TextAlign.CENTER);
            p.setSpaceAfter(0.0);
            for (XSLFTextRun run : p.getTextRuns()) {
                run.setFontFamily(FONT);
                run.setFontSize(11.0);
                run.setFontColor(fg);
                run.setBold(true);
            }
        }
    }

    private static XSLFTextBox text(XSLFSlide slide, double x, double y, double w, double h,
                                    double size, boolean bold, Color color, TextAlign align, String value) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(rect2d(x, y, w, h));
        box.clearText();
        box.setVerticalAlignment(VerticalAlignment.TOP);
        String[] lines = value.split("\n", -1);
        for (String line : lines) {
            XSLFTextParagraph p = box.addNewTextParagraph();
            p.setTextAlign(align);
            p.setSpaceAfter(0.0);
            p.setSpaceBefore(0.0);
            p.setLineSpacing(100.0);
            XSLFTextRun run = p.addNewTextRun();
            run.setText(line);
            run.setFontFamily(FONT);
            run.setFontSize(size);
            run.setBold(bold);
            run.setFontColor(color);
        }
        return box;
    }

    private static void bullets(XSLFSlide slide, double x, double y, double w, String[] lines, double size) {
        bullets(slide, x, y, w, lines, size, TEXT);
    }

    private static void bullets(XSLFSlide slide, double x, double y, double w, String[] lines, double size, Color color) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(rect2d(x, y, w, Math.min(H - y - 20, lines.length * size * 1.75 + 10)));
        box.clearText();
        box.setVerticalAlignment(VerticalAlignment.TOP);
        for (String line : lines) {
            XSLFTextParagraph p = box.addNewTextParagraph();
            p.setTextAlign(TextAlign.LEFT);
            p.setSpaceAfter(-3.0);
            p.setSpaceBefore(0.0);
            p.setLineSpacing(100.0);
            XSLFTextRun run = p.addNewTextRun();
            run.setText(line);
            run.setFontFamily(FONT);
            run.setFontSize(size);
            run.setFontColor(color);
        }
    }

    private static void table(XSLFSlide slide, double x, double y, double width,
                             String[] headers, String[][] rows, double[] widths,
                             double headerSize, double bodySize, int[] leftAligned) {
        XSLFTable table = slide.createTable(rows.length + 1, headers.length);
        table.setAnchor(rect2d(x, y, width, 10));
        double sum = 0;
        for (double w : widths) {
            sum += w;
        }
        double scale = width / sum;
        for (int c = 0; c < headers.length; c++) {
            table.setColumnWidth(c, widths[c] * scale);
        }
        table.setRowHeight(0, 27);
        for (int r = 0; r < rows.length; r++) {
            table.setRowHeight(r + 1, 24);
        }

        for (int c = 0; c < headers.length; c++) {
            XSLFTableCell cell = table.getCell(0, c);
            styleCell(cell, headers[c], headerSize, true, WHITE, NAVY,
                    contains(leftAligned, c) ? TextAlign.LEFT : TextAlign.CENTER);
        }
        for (int r = 0; r < rows.length; r++) {
            Color fill = r % 2 == 0 ? WHITE : LIGHT;
            for (int c = 0; c < headers.length; c++) {
                XSLFTableCell cell = table.getCell(r + 1, c);
                String value = c < rows[r].length ? rows[r][c] : "";
                boolean emphasize = c == 0;
                styleCell(cell, value, bodySize, emphasize, emphasize ? NAVY : TEXT, fill,
                        contains(leftAligned, c) ? TextAlign.LEFT : TextAlign.CENTER);
            }
        }
        table.updateCellAnchor();
    }

    private static void styleCell(XSLFTableCell cell, String value, double size, boolean bold,
                                  Color color, Color fill, TextAlign align) {
        XSLFTextRun run = cell.setText(value);
        run.setFontFamily(FONT);
        run.setFontSize(size);
        run.setBold(bold);
        run.setFontColor(color);
        cell.setFillColor(fill);
        cell.setVerticalAlignment(VerticalAlignment.MIDDLE);
        for (XSLFTextParagraph p : cell.getTextParagraphs()) {
            p.setTextAlign(align);
            p.setSpaceAfter(0.0);
            p.setSpaceBefore(0.0);
            p.setLineSpacing(92.0);
        }
    }

    private static boolean contains(int[] values, int target) {
        for (int v : values) {
            if (v == target) {
                return true;
            }
        }
        return false;
    }

    private static void picture(XSLFSlide slide, Path path, double x, double y, double w, double h) throws IOException {
        XSLFPictureData data = slide.getSlideShow().addPicture(Files.readAllBytes(path), PictureType.PNG);
        XSLFPictureShape pic = slide.createPicture(data);
        pic.setAnchor(rect2d(x, y, w, h));
    }

    private static Rectangle2D rect2d(double x, double y, double w, double h) {
        return new Rectangle2D.Double(x, y, w, h);
    }

    private static String f3(double value) {
        return String.format(Locale.ROOT, "%.3f", value).replace(',', '.');
    }

    private static String fmtNum(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private static String n0(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    // ------------------------------------------------------------------ charts (Java2D)

    private static void groupedBars(Path out, String title, String subtitle, String[] categories,
                                    String[] series, double[][] values, Color[] colors, double maxY) throws IOException {
        int width = 1000;
        int height = 560;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(img, width, height);
        g.setColor(WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(NAVY);
        g.setFont(new Font(FONT, Font.BOLD, 24));
        g.drawString(title, 40, 46);
        g.setColor(MUTED);
        g.setFont(new Font(FONT, Font.PLAIN, 14));
        g.drawString(subtitle, 40, 72);

        int left = 88;
        int right = width - 42;
        int top = 116;
        int bottom = height - 96;
        int plotW = right - left;
        int plotH = bottom - top;

        g.setFont(new Font(FONT, Font.PLAIN, 12));
        for (int i = 0; i <= 5; i++) {
            double v = maxY * i / 5.0d;
            int yy = bottom - (int) (plotH * i / 5.0d);
            g.setColor(new Color(0xE3E9EF));
            g.drawLine(left, yy, right, yy);
            g.setColor(MUTED);
            String label = maxY > 10 ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v);
            g.drawString(label, left - 44, yy + 4);
        }

        int nCat = categories.length;
        int nSeries = series.length;
        double groupW = (double) plotW / nCat;
        double barW = Math.min(48.0d, groupW * 0.72d / nSeries);

        for (int c = 0; c < nCat; c++) {
            double groupCenter = left + groupW * c + groupW / 2.0d;
            double startX = groupCenter - (barW * nSeries + 6 * (nSeries - 1)) / 2.0d;
            for (int si = 0; si < nSeries; si++) {
                double v = values[si][c];
                double barH = plotH * (v / maxY);
                int x = (int) Math.round(startX + si * (barW + 6));
                int y = (int) Math.round(bottom - barH);
                g.setColor(colors[si % colors.length]);
                g.fillRoundRect(x, y, (int) Math.round(barW), (int) Math.round(barH), 6, 6);
                g.setColor(TEXT);
                g.setFont(new Font(FONT, Font.BOLD, 12));
                String label = maxY > 10 ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.3f", v);
                int labelW = g.getFontMetrics().stringWidth(label);
                g.drawString(label, x + (int) barW / 2 - labelW / 2, y - 7);
            }
            g.setColor(TEXT);
            g.setFont(new Font(FONT, Font.BOLD, 13));
            String cat = categories[c];
            int catW = g.getFontMetrics().stringWidth(cat);
            g.drawString(cat, (int) groupCenter - catW / 2, bottom + 26);
        }

        int legendY = height - 20;
        int legendX = left;
        g.setFont(new Font(FONT, Font.PLAIN, 12));
        for (int i = 0; i < nSeries; i++) {
            String name = series[i];
            g.setColor(colors[i % colors.length]);
            g.fillRoundRect(legendX, legendY - 11, 12, 12, 3, 3);
            g.setColor(TEXT);
            g.drawString(name, legendX + 17, legendY);
            legendX += 17 + g.getFontMetrics().stringWidth(name) + 26;
        }

        g.setColor(new Color(0xC8D3DD));
        g.drawLine(left, bottom, right, bottom);
        g.drawLine(left, top, left, bottom);
        g.dispose();
        ImageIO.write(img, "png", out.toFile());
    }

    private static void horizontalBars(Path out, String title, String[] labels, double[] values,
                                       Color[] colors, double max) throws IOException {
        int width = 1000;
        int height = 460;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(img, width, height);
        g.setColor(WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(NAVY);
        g.setFont(new Font(FONT, Font.BOLD, 24));
        g.drawString(title, 40, 46);

        int left = 240;
        int right = width - 90;
        int top = 96;
        int rowH = 70;
        int plotW = right - left;

        for (int i = 0; i <= 4; i++) {
            int x = left + plotW * i / 4;
            g.setColor(new Color(0xE3E9EF));
            g.drawLine(x, top - 12, x, top + rowH * labels.length + 4);
            g.setColor(MUTED);
            g.setFont(new Font(FONT, Font.PLAIN, 11));
            String lab = String.format(Locale.ROOT, "%.0f", max * i / 4);
            g.drawString(lab, x - 8, top + rowH * labels.length + 26);
        }

        for (int i = 0; i < labels.length; i++) {
            int y = top + i * rowH;
            g.setColor(TEXT);
            g.setFont(new Font(FONT, Font.BOLD, 14));
            g.drawString(labels[i], 40, y + 20);
            int barW = (int) Math.round(plotW * values[i] / max);
            g.setColor(colors[i % colors.length]);
            g.fillRoundRect(left, y, Math.max(4, barW), 34, 8, 8);
            g.setColor(TEXT);
            g.setFont(new Font(FONT, Font.BOLD, 14));
            g.drawString(String.format(Locale.ROOT, "%.2f%%", values[i]), left + barW + 10, y + 22);
        }
        g.dispose();
        ImageIO.write(img, "png", out.toFile());
    }

    private static void confusionPanel(Path out, MR[] models, Confusion[] cms) throws IOException {
        int width = 1200;
        int height = 460;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(img, width, height);
        g.setColor(WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(NAVY);
        g.setFont(new Font(FONT, Font.BOLD, 24));
        g.drawString("Ma trận nhầm lẫn (lần chạy 42, lớp dương = thư lừa đảo)", 40, 46);

        int panelW = 340;
        int gap = 30;
        int startX = 40;
        int top = 90;
        for (int i = 0; i < models.length; i++) {
            int x = startX + i * (panelW + gap);
            g.setColor(TEXT);
            g.setFont(new Font(FONT, Font.BOLD, 15));
            g.drawString(models[i].shortName, x, top - 10);
            Confusion c = cms[i];
            int cw = 120;
            int ch = 90;
            cell(g, x, top + 12, cw, ch, "Âm đúng", c.tn, new Color(0xE7F3EC), GREEN);
            cell(g, x + cw + 8, top + 12, cw, ch, "Dương sai", c.fp, new Color(0xF9E7E4), RED);
            cell(g, x, top + 12 + ch + 8, cw, ch, "Âm sai", c.fn, new Color(0xF9E7E4), RED);
            cell(g, x + cw + 8, top + 12 + ch + 8, cw, ch, "Dương đúng", c.tp, new Color(0xE7F3EC), GREEN);
            g.setColor(MUTED);
            g.setFont(new Font(FONT, Font.PLAIN, 11));
            g.drawString("Đặc hiệu " + String.format(Locale.ROOT, "%.2f", c.spec)
                    + "   MCC " + String.format(Locale.ROOT, "%.3f", c.mcc), x, top + 12 + 2 * ch + 28);
        }
        g.dispose();
        ImageIO.write(img, "png", out.toFile());
    }

    private static void cell(Graphics2D g, int x, int y, int w, int h, String label, int value,
                             Color fill, Color accent) {
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(accent);
        g.setFont(new Font(FONT, Font.BOLD, 12));
        g.drawString(label, x + 10, y + 20);
        g.setColor(NAVY);
        g.setFont(new Font(FONT, Font.BOLD, 30));
        String v = String.valueOf(value);
        int vw = g.getFontMetrics().stringWidth(v);
        g.drawString(v, x + (w - vw) / 2, y + h / 2 + 12);
    }

    private static Graphics2D graphics(BufferedImage img, int width, int height) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(WHITE);
        g.fillRect(0, 0, width, height);
        return g;
    }

    private static double max(double[] values) {
        double m = 0.0001d;
        for (double v : values) {
            m = Math.max(m, v);
        }
        return m;
    }
}

