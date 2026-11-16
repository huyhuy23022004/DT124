package com.dt124.testutil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Reusable .eml fixtures for unit and negative tests.
 */
public final class EmlFixtures {

    private EmlFixtures() {
    }

    public static final String BENIGN_EML = """
            From: kay.mann@enron.com
            To: employee1@enron.com
            Subject: Meeting notes for the west power desk
            Date: Tue, 25 Mar 2002 15:07:00 -0500
            Message-ID: <benign-1@enron.com>
            Return-Path: <kay.mann@enron.com>
            Reply-To: kay.mann@enron.com
            Authentication-Results: mx.enron.com; spf=pass smtp.mailfrom=enron.com; dkim=pass header.d=enron.com; dmarc=pass header.from=enron.com
            Received-SPF: pass (mx.enron.com: domain of kay.mann@enron.com designates 192.0.2.10 as permitted sender)
            DKIM-Signature: v=1; a=rsa-sha256; d=enron.com; s=sel; h=from:to:subject;
            Received: from relay0 (relay0.example.net [192.0.2.100]) by mx0.example.com; Tue, 25 Mar 2002 14:53:00 -0500
            MIME-Version: 1.0
            Content-Type: text/plain; charset="UTF-8"

            Please review the attached schedule before Friday.
            Thanks,
            Colleague
            """;

    public static final String PHISHING_EML = """
            From: security@pay-ap.top
            To: victim1@example.com
            Subject: Your account has been suspended - immediate action required
            Date: Tue, 25 Mar 2002 15:07:00 -0500
            Message-ID: <phish-1@pay-ap.top>
            Return-Path: <bounce@mail-7f3a.top>
            Reply-To: recovery1@gmail.com
            Authentication-Results: mx.example.com; spf=fail smtp.mailfrom=mail-7f3a.top; dkim=fail header.d=pay-ap.top; dmarc=fail header.from=pay-ap.top
            Received-SPF: fail (mx.example.com: domain of security@pay-ap.top does not designate 203.0.113.10 as permitted sender)
            DKIM-Signature: v=1; a=rsa-sha256; d=pay-ap.top; s=default; h=from:to:subject;
            Received: from relay0 (relay0.example.net [192.0.2.100]) by mx0.example.com; Tue, 25 Mar 2002 14:53:00 -0500
            Received: from relay1 (relay1.example.net [192.0.2.101]) by mx1.example.com; Tue, 25 Mar 2002 14:53:00 -0500
            MIME-Version: 1.0
            Content-Type: multipart/alternative; boundary="BOUND"

            --BOUND
            Content-Type: text/plain; charset="UTF-8"

            Verify your account immediately. Click here: http://pay-ap.top/login
            --BOUND
            Content-Type: text/html; charset="UTF-8"

            <html><body onload="x()"><a href="http://pay-ap.top/login">https://www.paypal.com/signin</a>
            <form action="http://pay-ap.top/login"><input type="password" style="display:none"/></form>
            <script>eval(atob('QUJDRA=='))</script></body></html>
            --BOUND--
            """;

    public static String phishingWithAttachment(String fileName, byte[] payload) {
        String base64 = Base64.getEncoder().encodeToString(payload);
        return """
                From: notice@apple-verify.top
                To: victim2@example.com
                Subject: Document enclosed
                Date: Tue, 25 Mar 2002 15:07:00 -0500
                Message-ID: <attach-1@apple-verify.top>
                Return-Path: <bounce@mail-aa.top>
                Reply-To: recovery2@gmail.com
                Authentication-Results: mx.example.com; spf=fail; dkim=fail; dmarc=fail
                MIME-Version: 1.0
                Content-Type: multipart/mixed; boundary="B"

                --B
                Content-Type: text/plain; charset="UTF-8"

                Please find the document enclosed.
                --B
                Content-Type: application/pdf; name="%s"
                Content-Transfer-Encoding: base64
                Content-Disposition: attachment; filename="%s"

                %s
                --B--
                """.formatted(fileName, fileName, base64);
    }

    public static byte[] peBytes() {
        byte[] bytes = new byte[128];
        bytes[0] = 'M';
        bytes[1] = 'Z';
        bytes[2] = (byte) 0x90;
        bytes[3] = 0x00;
        for (int i = 4; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 31);
        }
        return bytes;
    }

    public static byte[] pdfBytes() {
        byte[] header = "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n".getBytes(StandardCharsets.US_ASCII);
        byte[] bytes = new byte[128];
        System.arraycopy(header, 0, bytes, 0, header.length);
        for (int i = header.length; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 7);
        }
        return bytes;
    }
}
