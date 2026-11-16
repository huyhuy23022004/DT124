# Kịch bản thuyết trình 5 phút — Đề tài 124

**Sinh viên:** Bùi Ngọc Huy · **GVHD:** Phạm Trọng Huynh · **Học kỳ:** Giữa kỳ 11/2026
**Tổng thời lượng:** 4 phút 55 giây + 5 giây đệm

| Trang | Nội dung | Thời lượng |
|---|---|---|
| 1 | Mở đầu | 0:10 |
| 2 | Tổng quan bài báo & mục tiêu | 0:30 |
| 3 | Phương pháp & mô hình dữ liệu | 0:30 |
| 4 | Tái lập CH1–CH4 | 0:35 |
| 5 | Hạn chế & kiến trúc Java | 0:30 |
| 6 | Thiết kế mô hình cải tiến | 0:25 |
| 7 | Kết quả & chỉ số an ninh | 0:35 |
| 8 | Trực quan hoá & loại bỏ đặc trưng | 0:30 |
| 9 | Phân tích lỗi & chi phí | 0:25 |
| 10 | Kết luận & hướng phát triển | 0:35 |
| 11 | Tài liệu & cảm ơn | 0:10 |

---

## Trang 1 — Mở đầu (0:10)

**Lời thoại:**
Kính chào thầy và các bạn. Em là Bùi Ngọc Huy, đề tài 124: **Phân tích thư điện tử lừa đảo ngoại tuyến từ tệp .eml**. Bài gồm 3 phần: tái lập bài báo tyag020, xây quy trình Java ngoại tuyến và mô hình học máy cải tiến vượt mô hình cơ sở.

**Câu chốt:** 100% ngoại tuyến, không thực thi mã, 38/38 bài kiểm thử đạt.

---

## Trang 2 — Tổng quan bài báo & mục tiêu (0:30)

**Lời thoại:**
Bài báo gốc là **“Examining newly registered phishing domains at scale”** của Agarwal & Vasek 2026, dịch là **“Nghiên cứu quy mô lớn các tên miền lừa đảo mới đăng ký”**. Nhóm tác giả phân tích **15.126 tên miền mới trong 11 tháng**, làm giàu bằng WHOIS, DNS thụ động, nhật ký CT, ASN và GSB, trả lời 4 câu hỏi nghiên cứu (CH1–CH4). Đề tài em tái lập 4 câu hỏi trên phần dữ liệu suy ra được từ **.eml**, dựng lại mô hình 20+ trường và cải tiến học máy.

**Số nhấn:** 15.126 tên miền · 11 tháng · 5 nguồn dữ liệu.
**Chuyển:** Để tái lập, em bám theo mô hình của bài báo.

---

## Trang 3 — Phương pháp & mô hình dữ liệu (0:30)

**Lời thoại:**
Bài báo có 2 mô hình tấn công: **Hình 1** là chiếm website hợp pháp; **Hình 2** là mua tên miền mới, thuê AS và xin chứng chỉ TLS. Đề tài tập trung vào **tên miền mới**. Em dựng lại lược đồ **20+ trường**, chia 5 nhóm: định danh, hạ tầng, thương hiệu, vòng đời, phân giải — từ đó trích **30 đặc trưng** hoàn toàn ngoại tuyến.

**Số nhấn:** 20+ trường · 5 nhóm · 30 đặc trưng.
**Chuyển:** Vậy tái lập được đến đâu?

---

## Trang 4 — Tái lập CH1–CH4 (0:35)

**Lời thoại:**
Đây là bảng đối chiếu **bài báo và phần tái lập ngoại tuyến**. Phần suy ra được từ .eml em báo cáo: **lưu trữ miễn phí 32,43%**, **thương hiệu + gạch nối 54,05%**, **tên miền con 60%**. Phần cần truy vấn trực tuyến như >50 chứng chỉ, Cloudflare, vòng đời 8,6 ngày, tái đăng ký 25,7% thì em ghi **“không có”**, không đoán. Bên phải là **Bảng 12** của bài báo: GSB API phát hiện **3.012** tên miền, báo cáo minh bạch **3.981**, VirusTotal **2.508** — cho thấy điểm mù của GSB.

**Câu chốt:** “không có” là trung thực, không phải thiếu.
**Chuyển:** Từ hạn chế của bài báo, em chuyển sang kiến trúc ngoại tuyến.

---

## Trang 5 — Hạn chế & kiến trúc Java (0:30)

**Lời thoại:**
Bài báo phụ thuộc WHOIS/DNS/CT trực tuyến, bỏ qua nội dung thư, không kiểm tra xác thực, không phân tích tệp đính kèm và không có học máy. Em xử lý bằng **4 mô-đun**: bộ phân tích/trích xuất, xác thực, tệp đính kèm/YARA, học máy. Quy trình chạy **100% ngoại tuyến, không thực thi mã**: **505 tệp .eml**, **250/250**, độ phủ **100%**, **1,714 ms/thư**, **54 MB**; **38/38 bài kiểm thử đạt**.

**Số nhấn:** 5 hạn chế → 4 mô-đun · không truy cập mạng.
**Chuyển:** Mô hình cải tiến được thiết kế thế nào?

---

## Trang 6 — Thiết kế mô hình cải tiến (0:25)

**Lời thoại:**
Mô hình dùng **30 đặc trưng**, chia 4 khối: cơ sở **3**, tiêu đề – xác thực **10**, URL bài báo **8**, tệp đính kèm + YARA **10**. Tổng **31 lượt** nhưng **30 duy nhất** vì `url_count` dùng chung. Em kết hợp **0,65 RandomForest + 0,35 điểm luật**, và chống rò rỉ bằng **tập giữ lại rời rạc theo tên miền người gửi**.

**Câu chốt:** 30 duy nhất · kết hợp 65/35 · không rò rỉ.
**Chuyển:** Kết quả ra sao?

---

## Trang 7 — Kết quả & chỉ số an ninh (0:35)

**Lời thoại:**
Kết quả kiểm định chéo 5 phần × 3 lần chạy: mô hình cơ sở F1 **0,736**; chỉ dùng URL **0,750**; mô hình đề xuất **0,997 ± 0,002**. ROC-AUC và PR-AUC của mô hình đề xuất đạt **1,00**. Ma trận nhầm lẫn lần chạy 42: mô hình đề xuất **250 dương đúng, 250 âm đúng, 0 dương sai, 0 âm sai**; độ đặc hiệu **1,00**; MCC **1,000**. Mô hình cơ sở có **100 dương sai** và MCC **0,399**; chỉ dùng URL có độ bao phủ **0,60** vì bỏ sót thư không có URL. Chênh lệch F1 **+0,2609**.

**Số nhấn:** 0,997 · độ đặc hiệu 1,00 · MCC 1,000.
**Chuyển:** Hai biểu đồ và phần loại bỏ đặc trưng xác nhận điều này.

---

## Trang 8 — Trực quan hoá & loại bỏ đặc trưng (0:30)

**Lời thoại:**
Hai biểu đồ cho thấy mô hình đề xuất vượt trội ở F1, ROC, PR và độ đặc hiệu. Loại bỏ đặc trưng: bỏ **nhóm xác thực tiêu đề** làm F1 giảm còn **0,945**, độ bao phủ **0,896**, MCC **0,901**; bỏ URL bài báo gần như không đổi; bỏ tệp đính kèm + YARA còn **0,996**. Vậy **xác thực thư** là nhóm quan trọng nhất.

**Câu chốt:** bỏ xác thực tiêu đề → F1 0,945.
**Chuyển:** Lỗi còn lại và chi phí.

---

## Trang 9 — Phân tích lỗi & chi phí (0:25)

**Lời thoại:**
Lỗi còn lại chủ yếu là **âm tính giả**: thư chỉ có tệp đính kèm, không URL rõ ràng. Mô hình cơ sở có **tỷ lệ báo nhầm 0,40**, tức **100/250 thư lành** bị báo động sai. Chi phí thấp: **1,714 ms/thư**, **857 ms cho 500 thư**, **54 MB bộ nhớ**, không gọi mạng.

**Số nhấn:** âm tính giả do tệp đính kèm · 100/250 báo nhầm · 1,7 ms.
**Chuyển:** Tổng kết.

---

## Trang 10 — Kết luận & hướng phát triển (0:35)

**Lời thoại:**
Tổng kết: em **tái lập trung thực** phần ngoại tuyến, ghi rõ “không có” phần trực tuyến; **cải tiến** đạt F1 **0,9973**, độ đặc hiệu **1,00**, MCC **0,9947**; **tập giữ lại rời rạc theo nhóm 0,41 so với 1,00** chứng minh không rò rỉ tên miền. Hướng tiếp theo: bổ sung tập dữ liệu Enron thật + thư lừa đảo .eml công khai, thêm đường cong ROC/PR, mở rộng luật YARA, đóng gói dòng lệnh/Docker.

**Câu chốt:** 3 thông điệp: trung thực — cải tiến có kiểm chứng — chống rò rỉ.
**Chuyển:** Tài liệu và lời cảm ơn.

---

## Trang 11 — Tài liệu & cảm ơn (0:10)

**Lời thoại:**
Tài liệu gồm bài báo gốc, Weka và Enron. Em sẵn sàng trình diễn trực tiếp quy trình và chạy lại kết quả. Cảm ơn thầy và các bạn đã theo dõi.

---

## Nếu thầy hỏi nhanh

**1. Tên bài báo và dịch nghĩa?**
“Examining newly registered phishing domains at scale” — Nghiên cứu quy mô lớn các tên miền lừa đảo mới đăng ký.

**2. Vì sao nhiều chỉ số ghi “không có”?**
Vì cần WHOIS lịch sử, nhật ký CT, DNS/ASN hoặc GSB trực tuyến — đề tài bắt buộc ngoại tuyến nên em không suy diễn.

**3. Vì sao 30 đặc trưng mà có nhóm ghi 3+10+8+10 = 31?**
31 là số lượt; `url_count` nằm ở cả nhóm cơ sở và URL bài báo nên duy nhất còn 30.

**4. Vì sao mô hình đề xuất tốt hơn?**
Kết hợp xác thực tiêu đề + URL + tệp đính kèm/YARA + luật, bắt được cả thư không có URL; tập giữ lại 0,41 → 1,00 chứng minh không rò rỉ.

**5. Sao không dùng Enron thật ngay?**
Quy trình đã nối sẵn `config.enronDir`; dùng dữ liệu tổng hợp có kiểm soát để tái lập và trình diễn ổn định, Enron thật là hướng cuối kỳ.

**6. Điểm mạnh học thuật?**
Tái lập trung thực (phân biệt rõ tái lập được/không có), đánh giá đủ chỉ số (F1, ROC, PR, tỷ lệ báo nhầm, độ đặc hiệu, MCC), chống rò rỉ bằng tập giữ lại rời rạc theo nhóm.

---

## Mẹo giữ đúng 5 phút

- Mỗi trang chỉ nói **2–3 câu**, không đọc bảng.
- Chỉ nhấn **1–2 con số** mỗi trang.
- Trang 4, 7, 8 là trọng tâm — nói chậm hơn một chút.
- Nếu hết 4:30 mà còn trang 10–11: nói câu chốt rồi sang cảm ơn.
- Không mở ghi chú trừ khi thầy hỏi chi tiết.
