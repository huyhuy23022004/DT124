# ĐẶC TẢ YÊU CẦU ĐỀ TÀI DT124 - BẢN ĐẦY ĐỦ CHUẨN JAVA (FULL REQUIREMENTS SPECIFICATION)

## 1. Thông tin chung & Quy định chấm điểm của Giảng viên
- **Mã đề tài:** DT124
- **Môn học:** An toàn và Bảo mật Hệ thống Thông tin (2026 - 2027)
- **Giảng viên hướng dẫn:** Thầy Phạm Trọng Huynh
- **Tên đề tài:** Phân tích email phishing ngoại tuyến từ tệp EML (Offline Phishing Email Analysis from EML Files)
- **Ngôn ngữ lập trình bắt buộc:** **JAVA (JDK 17+ kết hợp Apache Maven `pom.xml`)**
- **Mục tiêu cốt lõi:** Xây dựng và đánh giá một nguyên mẫu/khung thực nghiệm bằng Java về trích xuất header, authentication results, URLs và attachment metadata mà KHÔNG mở hoặc thực thi nội dung nguy hiểm (zero-execution / offline static analysis); tái lập có chọn lọc bài báo Q1 gần đây, sau đó tự xây dựng model/thuật toán cải tiến cho kết quả tốt hơn.

---

## 2. Yêu cầu trọng tâm cho Báo cáo Giữa kỳ (Midterm Deliverable - Mục tiêu 9-10 điểm)
Theo quy định chấm điểm của thầy Phạm Trọng Huynh:
- **Hình thức báo cáo Giữa kỳ (Làm cá nhân):** Chỉ làm **Slide báo cáo + Chạy Demo kết quả trực tiếp** (Không cần làm file Word ở giữa kỳ; file Word 20-30 trang nộp vào Cuối kỳ).
- **Khung điểm đánh giá Giữa kỳ:**
  - **Mức [6 - 8 điểm]:** Cá nhân chọn đề tài, dịch bài báo gốc, dựng lại mô hình dữ liệu của bài báo, biết chạy lại data và chạy lại đúng kết quả trên tập dataset của bài báo.
  - **Mức [9 - 10 điểm] (Mục tiêu của dự án này):** Thực hiện đầy đủ mức trên VÀ **tự xây dựng thêm Models, thuật toán cải tiến bằng Java** chạy lại kết quả từ tập dataset cho ra kết quả (F1-score, Precision, Recall, ROC-AUC, PR-AUC) **tốt hơn rõ rệt so với Baseline/bài báo**.

---

## 3. Tài liệu nền & Phiếu tái lập bài báo Q1 (Bước 1)
- **Tên bài báo chính:** "Examining newly registered phishing domains at scale"
- **Tác giả:** Sharad Agarwal, Marie Vasek (University College London - UCL)
- **Tạp chí:** Journal of Cybersecurity (Oxford Academic - Q1), Vol. 12, Issue 1, 2026
- **DOI:** `10.1093/cybsec/tyag020`

### 3.1. Mô hình dữ liệu (Data Schema) và các kết quả mục tiêu cần tái lập từ bài báo:
- **Quy mô dữ liệu bài báo:** 15.126 tên miền phishing đăng ký mới (Newly Registered Domains - NRDs) thu thập trong 11 tháng.
- **RQ1 (Đặc điểm hạ tầng mạng, DNS, ASN & Chứng chỉ TLS):**
  - Thống kê phân bố Top TLDs bị lạm dụng (đối chiếu với báo cáo của Kaspersky).
  - Hơn **79.3%** tên miền phishing mới có chứng chỉ TLS hợp lệ (cao hơn mức 66.7% của mặt bằng chung web phishing); có 190 tên miền được cấp >50 chứng chỉ TLS.
  - **26%** tên miền lạm dụng dịch vụ proxy của Cloudflare; lạm dụng dịch vụ web hosting miễn phí (đặc biệt là Google Firebase `web.app`) và tập trung ở các ASN mới thành lập (< 2 năm tuổi).
- **RQ2 (Thương hiệu và lĩnh vực bị giả mạo - Brand Impersonation):**
  - Phân bố các ngành nghề bị nhắm mục tiêu (Delivery/Logistics như DHL/USPS, Financial/Banking, Tech...).
  - Kẻ tấn công thường đăng ký tên miền mới ngắn gọn, có chứa tên thương hiệu kết hợp dấu gạch ngang `-` (ví dụ: `pay-ap[.]com` giả mạo Apple Pay).
- **RQ3 (Vòng đời hoạt động & Tái đăng ký tên miền):**
  - Thời gian hoạt động trung bình (Active Lifetime) của một NRD phishing là **8.6 ngày** (dài hơn nhiều so với 21 giờ của các URL phishing thông thường).
  - Có tới **25.7%** tên miền là tên miền cũ hết hạn được đăng ký lại (Re-registered domains), đa số từng phục vụ mục đích độc hại trong quá khứ.
- **RQ4 (Khả năng phát hiện của Google Safe Browsing - GSB):**
  - Có sự bất đồng bộ lớn giữa kết quả truy vấn qua GSB API và nền tảng trình duyệt Web đối với các trang phishing đặt trên subdomain.

### 3.2. Năm (05) hạn chế của bài báo và hướng mở rộng của đề tài DT124:
1. **Hạn chế 1 (Phụ thuộc truy vấn trực tuyến WHOIS/DNS/CT logs/GSB API):** Chuyển hóa thành bộ đặc trưng tĩnh ngoại tuyến (Offline Lexical & Structural URL/Domain Features) trích xuất trực tiếp từ tệp `.eml` bằng Java.
2. **Hạn chế 2 (Thiếu ngữ cảnh truyền tải Email - Delivery Context):** Bổ sung phân tích Header email, chuỗi `Received` hops, độ lệch miền gửi `From` vs `Return-Path`/`Reply-To` và kiểm tra kết quả xác thực `SPF`, `DKIM`, `DMARC`.
3. **Hạn chế 3 (Điểm mù phát hiện trên Subdomain của GSB API):** Bổ sung kiểm tra độ sâu subdomain, dịch vụ free-hosting (`web.app`, `pages.dev`, `firebaseapp.com`, `vercel.app`) và độ lệch liên kết HTML (`<a href="domain_A">domain_B</a>`).
4. **Hạn chế 4 (Dữ liệu gốc của bài báo là dữ liệu đóng từ đối tác):** Tái lập khung đánh giá minh bạch trên **Enron Email Dataset** kết hợp tập mẫu **Labeled Phishing EML** công khai/tổng hợp có kiểm soát.
5. **Hạn chế 5 (Không xét tệp đính kèm - Attachments):** Bổ sung module Java trích xuất **Safe Attachment Metadata** (tên file, double extension, MIME type vs Magic bytes header, Shannon Entropy) và quét **YARA-style Safe Indicators** trong bộ nhớ RAM mà không mở hay thực thi file.

---

## 4. Yêu cầu về Dữ liệu, Khử trùng lặp & Ngăn rò rỉ (Bước 2 & Bước 4)
- **Nguồn dữ liệu chính:** Enron Email Dataset (`https://www.cs.cmu.edu/~enron/`), phiên bản ngày **May 7, 2015** (`enron_mail_20150507.tar.gz`).
- **Hồ sơ dữ liệu (Data Manifest):** Chương trình Java phải ghi nhận phiên bản/ngày tải, điều khoản sử dụng, tính toán mã băm `SHA-256` xuất ra file `data/manifests/checksums_sha256.txt` và thống kê chất lượng dữ liệu.
- **Tạo Labeled Subset phục vụ huấn luyện và tính F1-score:**
  - Bản CMU Enron gốc chủ yếu là email doanh nghiệp lành tính (nhãn `0 - Benign`) và đã lược bỏ đính kèm. Do đó, hệ thống cần module nạp kết hợp tập Enron với tập mẫu `.eml` nhãn `1 - Phishing` (từ kho Phishing EML công khai + bộ generator tạo mẫu `.eml` kiểm thử có đầy đủ header `SPF/DKIM`, URLs theo mẫu bài báo `tyag020` và attachment giả lập an toàn).
- **Khử trùng lặp (Deduplication) & Chống rò rỉ Train-Test (Anti-Leakage):**
  - Băm `SHA-256(Normalized_Subject + Normalized_Body)` hoặc `Message-ID` bằng `java.security.MessageDigest` để loại bỏ toàn bộ email trùng lặp giữa các thư mục (`inbox`, `sent`, `all_documents`).
  - Chia tập dữ liệu theo nhóm người gửi/luồng hội thoại (Group-based split) và **tuyệt đối không đưa trường Năm tuyệt đối (`Year`) vào vector đặc trưng** để ngăn rò rỉ thời gian (Temporal Artifact Leakage) giữa email Enron (1999-2002) và email Phishing hiện đại.

---

## 5. Kiến trúc Công nghệ JAVA & Triển khai cốt lõi (Bước 3 & Bước 5)
### 5.1. Công nghệ & Thư viện trong `pom.xml` (Maven):
- **Java Version:** JDK 17 hoặc JDK 21.
- **Đọc và parse tệp `.eml` ngoại tuyến:** `com.sun.mail:jakarta.mail` (Jakarta Mail API - `MimeMessage`, `MimeMultipart`, `MimeBodyPart`).
- **Phân tích HTML & Trích xuất URL:** `org.jsoup:jsoup` + `java.net.URI` + Regex.
- **Xử lý CSV, JSON & Logging:** `org.apache.commons:commons-csv`, `com.fasterxml.jackson.core:jackson-databind`, `org.slf4j:slf4j-simple`.
- **Machine Learning & Đánh giá thực nghiệm:** `nz.ac.waikato.cms.weka:weka-stable` (Weka Java API) để huấn luyện, chạy Cross-Validation và so sánh các mô hình phân loại.
- **Kiểm thử phần mềm:** `org.junit.jupiter:junit-jupiter` (JUnit 5).

### 5.2. Cấu trúc các Module Java cần cài đặt (`src/main/java/com/dt124/`):
1. **`model/PaperDomainRecord.java` & `model/EmailFeatureRecord.java`:**
   - Biểu diễn đầy đủ Data Schema của bài báo `tyag020` và 4 nhóm đặc trưng trích xuất từ tệp `.eml`.
2. **`parser/OfflineEmlParser.java`:**
   - Đọc tệp `.eml` bằng `MimeMessage(Session, InputStream)` hoàn toàn ngoại tuyến; xử lý ngoại lệ an toàn cho các tệp MIME lỗi/dị dạng; thống kê **Parser Coverage (%)** và khử trùng lặp bằng SHA-256.
3. **`extractor/AuthHeaderValidator.java`:**
   - Trích xuất `From`, `To`, `Subject`, `Date`, `Return-Path`, `Reply-To`, đếm số trạm `Received` hops.
   - Phân tích trường `Authentication-Results`, `Received-SPF`, `DKIM-Signature` để xác định trạng thái `SPF` (`pass/fail/softfail/missing`), `DKIM` (`pass/fail/missing`), `DMARC` và cờ báo động `from_return_path_mismatch`, `from_reply_to_mismatch`.
4. **`extractor/UrlDomainAnalyzer.java` (Tái lập đặc trưng bài báo `tyag020`):**
   - Trích xuất toàn bộ URL từ phần `text/plain` và `text/html` (dùng Jsoup).
   - Tính toán bộ đặc trưng của bài báo: số lượng URL (`url_count`), URL chứa địa chỉ IP (`url_ip_count`), lạm dụng dịch vụ hosting miễn phí (`url_free_hosting_abuse`: `web.app`, `firebaseapp.com`, `pages.dev`, `workers.dev`, `ngrok.io`...), TLD rủi ro cao (`url_suspicious_tld`: `.xyz`, `.top`, `.icu`, `.vip`, `.shop`, `.click`...), tên miền giả mạo thương hiệu có dấu gạch ngang (`url_brand_hyphen`: `paypal`, `apple`, `dhl`, `usps`, `microsoft`, `netflix`...), độ sâu subdomain tối đa (`url_max_subdomains`), entropy tên miền, và độ lệch thẻ liên kết HTML (`html_anchor_mismatch` khi domain trong `href` khác với domain hiển thị).
5. **`extractor/SafeAttachmentInspector.java`:**
   - Duyệt các `MimeBodyPart` đính kèm mà **không ghi ra đĩa dưới dạng thực thi và không mở file**.
   - Đọc tối đa 5MB đầu tiên trong bộ nhớ RAM để chống tấn công DoS/Zip Bomb.
   - Trích xuất: số lượng đính kèm (`att_count`), phần mở rộng nguy hiểm (`.exe`, `.scr`, `.js`, `.vbs`, `.hta`, `.iso`, `.lnk`, `.html`), phần mở rộng kép (`att_double_ext` như `invoice.pdf.exe`), kiểm tra lệch **Magic Bytes** (`att_magic_mismatch` - ví dụ tên file `.pdf` nhưng byte đầu là `4D 5A` của file thực thi Windows PE), và tính **Shannon Entropy** (`att_max_entropy`).
6. **`extractor/SafeIndicatorRuleEngine.java` (YARA-style Rules):**
   - Đọc file luật `rules/safe_indicators.yar` (hoặc tập quy tắc biểu thức chính quy/byte patterns tương đương YARA trong Java) để quét các chỉ báo nguy hiểm trong header, HTML body (như `eval(`, `base64`, `onload=`, form đăng nhập ẩn) và attachment metadata.
7. **`ml/PaperReplicationService.java` & `ml/ModelExperimentRunner.java`:**
   - **Tái lập bài báo:** Tái hiện đúng bảng thống kê RQ1 -> RQ4 của bài báo `tyag020` và bảng đối chiếu trên tập dữ liệu `.eml`.
   - **Huấn luyện & So sánh 3 Models (Đạt mục tiêu 9-10 điểm):**
     * **Model 1 - Baseline:** Mô hình cơ sở (`NaiveBayes` / `Logistic` hoặc Rule-based cơ bản chỉ dựa trên từ khóa/số lượng URL đơn giản).
     * **Model 2 - Paper URL-Only Variant:** Chỉ sử dụng nhóm đặc trưng URL/Domain theo bài báo `tyag020`.
     * **Model 3 - Proposed Full Model (Đóng góp cải tiến của cá nhân):** Kết hợp toàn bộ 4 nhóm đặc trưng (Header/Auth + Paper `tyag020` URL/Domain + Safe Attachment Entropy/MagicBytes + YARA Safe Indicators) kết hợp thuật toán **Random Forest / Cost-Sensitive Ensemble (hoặc Weighted Hybrid Scoring + RandomForest)** cho kết quả F1-score vượt trội.
8. **`tests/OfflineEmlSecurityTest.java` (JUnit 5):**
   - Kiểm thử `unit tests`, `integration tests` và `negative tests` (tệp `.eml` bị cắt cụt, sai bảng mã charset, header giả mạo, đính kèm giả mạo magic bytes, tên file chứa path traversal `../../evil.exe`).

---

## 6. Yêu cầu Thực nghiệm, Đánh giá & Đóng gói Demo (Bước 6, 7, 8)
- **Ma trận thực nghiệm có kiểm soát:**
  - Chạy đánh giá với **5-Fold Cross-Validation** lặp lại trên **tối thiểu 3 bộ Random Seed cố định** (`seeds = [42, 100, 2026]`).
  - Lưu cấu hình vào `configs/experiment_config.json`, lưu toàn bộ kết quả thô và bảng tổng hợp vào thư mục `artifacts/`.
- **Các chỉ số đánh giá bắt buộc (Không chỉ báo cáo Accuracy):**
  1. **Parser Coverage (%):** Tỷ lệ tệp `.eml` được parse thành công trên tổng số tệp đầu vào.
  2. **Classification Metrics trên Labeled Subset:** Báo cáo đầy đủ **Precision, Recall, F1-score (Phishing F1 & Macro-F1), ROC-AUC, PR-AUC, False Positive Rate (FPR)** dưới dạng `Mean ± SD` (kèm khoảng tin cậy 95% CI).
  3. **Ablation Study (Phân tích loại bỏ thành phần):** Bảng so sánh hiệu năng khi loại bỏ lần lượt từng nhóm đặc trưng (Full Model vs. w/o Header-Auth vs. w/o Paper-URL vs. w/o Attachment-YARA).
  4. **Error Analysis & Chi phí tính toán:** In ra các ca False Positive (FP) và False Negative (FN) tiêu biểu để phân tích nguyên nhân; đo thời gian xử lý trung bình (`ms/email`) và bộ nhớ RAM tiêu thụ (`MB`).
- **Ràng buộc An toàn & Đạo đức:**
  - Chỉ thao tác ngoại tuyến 100% trên dữ liệu công khai/mẫu kiểm thử hợp pháp; tuyệt đối không gửi request tới các tên miền hay URL độc hại thật; ẩn danh hóa dữ liệu cá nhân (PII) trong log xuất ra.