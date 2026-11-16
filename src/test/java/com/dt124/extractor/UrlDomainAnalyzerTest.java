package com.dt124.extractor;

import com.dt124.model.ParsedEmail;
import com.dt124.parser.OfflineEmlParser;
import com.dt124.testutil.EmlFixtures;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlDomainAnalyzerTest {

    private final OfflineEmlParser parser = new OfflineEmlParser();
    private final UrlDomainAnalyzer analyzer = new UrlDomainAnalyzer();

    @Test
    void extractsUrisAndFlagsSuspiciousTldAndAnchorMismatch() {
        ParsedEmail email = parser.parseBytes(EmlFixtures.PHISHING_EML.getBytes(StandardCharsets.UTF_8), "phish");
        UrlAnalysis analysis = analyzer.analyze(email);

        assertTrue(analysis.getUrlCount() >= 1);
        assertTrue(analysis.isSuspiciousTld(), "pay-ap.top uses a high-risk TLD");
        assertTrue(analysis.isHtmlAnchorMismatch(), "href pay-ap.top vs visible paypal.com");
    }

    @Test
    void flagsBrandHyphenDomain() {
        String eml = "From: a@b.com\nSubject: s\nContent-Type: text/plain\n\n"
                + "https://apple-verify.top/login\n";
        ParsedEmail email = parser.parseBytes(eml.getBytes(StandardCharsets.UTF_8), "brandhyphen");
        UrlAnalysis analysis = analyzer.analyze(email);

        assertTrue(analysis.isBrandHyphen(), "apple-verify.top contains brand 'apple' + hyphen");
    }

    @Test
    void computesStaticHelpersCorrectly() {
        assertEquals("www.example.com", UrlDomainAnalyzer.hostOf("https://www.example.com/path?q=1"));
        assertEquals("example.co.uk", UrlDomainAnalyzer.registrableDomain("login.secure.example.co.uk"));
        assertEquals(2, UrlDomainAnalyzer.subdomainDepth("login.secure.example.co.uk"));
        assertEquals(0, UrlDomainAnalyzer.subdomainDepth("example.com"));
        assertTrue(UrlDomainAnalyzer.sameRegistrableDomain("a.example.com", "b.example.com"));
        assertFalse(UrlDomainAnalyzer.sameRegistrableDomain("example.com", "example.net"));
    }

    @Test
    void flagsFreeHostingAndIpUrls() {
        String eml = "From: a@b.com\nSubject: s\nContent-Type: text/plain\n\n"
                + "http://203.0.113.7/login https://evil.web.app/verify\n";
        ParsedEmail email = parser.parseBytes(eml.getBytes(StandardCharsets.UTF_8), "freehosting");
        UrlAnalysis analysis = analyzer.analyze(email);

        assertEquals(1, analysis.getIpUrlCount());
        assertTrue(analysis.isFreeHostingAbuse());
    }

    @Test
    void benignMailHasNoPhishingUrlSignals() {
        ParsedEmail email = parser.parseBytes(EmlFixtures.BENIGN_EML.getBytes(StandardCharsets.UTF_8), "benign");
        UrlAnalysis analysis = analyzer.analyze(email);

        assertFalse(analysis.isFreeHostingAbuse());
        assertFalse(analysis.isBrandHyphen());
        assertEquals(0, analysis.getIpUrlCount());
    }
}
