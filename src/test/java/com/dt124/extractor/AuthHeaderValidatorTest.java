package com.dt124.extractor;

import com.dt124.model.AuthResult;
import com.dt124.model.ParsedEmail;
import com.dt124.parser.OfflineEmlParser;
import com.dt124.testutil.EmlFixtures;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthHeaderValidatorTest {

    private final OfflineEmlParser parser = new OfflineEmlParser();
    private final AuthHeaderValidator validator = new AuthHeaderValidator();

    @Test
    void passesForLegitimateMessage() {
        ParsedEmail email = parser.parseBytes(EmlFixtures.BENIGN_EML.getBytes(StandardCharsets.UTF_8), "benign");
        AuthResult result = validator.validate(email);

        assertEquals(AuthResult.Status.PASS, result.getSpf());
        assertEquals(AuthResult.Status.PASS, result.getDkim());
        assertEquals(AuthResult.Status.PASS, result.getDmarc());
        assertFalse(result.isFromReturnPathMismatch());
        assertFalse(result.isFromReplyToMismatch());
    }

    @Test
    void detectsSpoofedSenderAndFailedAuth() {
        ParsedEmail email = parser.parseBytes(EmlFixtures.PHISHING_EML.getBytes(StandardCharsets.UTF_8), "phish");
        AuthResult result = validator.validate(email);

        assertEquals(AuthResult.Status.FAIL, result.getSpf());
        assertEquals(AuthResult.Status.FAIL, result.getDkim());
        assertEquals(AuthResult.Status.FAIL, result.getDmarc());
        assertTrue(result.isFromReturnPathMismatch(), "From pay-ap.top vs Return-Path mail-*.top");
        assertTrue(result.isFromReplyToMismatch(), "From domain vs free-mail Reply-To");
        assertTrue(result.isReplyToFreeMail(), "Reply-To gmail.com must be flagged as free mail");
    }

    @Test
    void reportsMissingAuthWhenHeadersAbsent() {
        String eml = "From: a@b.com\nSubject: hi\n\nbody\n";
        ParsedEmail email = parser.parseBytes(eml.getBytes(StandardCharsets.UTF_8), "noauth");
        AuthResult result = validator.validate(email);

        assertEquals(AuthResult.Status.MISSING, result.getSpf());
        assertEquals(AuthResult.Status.MISSING, result.getDmarc());
    }

    @Test
    void extractsDomainFromDisplayNameAddress() {
        assertEquals("paypal.com", AuthHeaderValidator.extractDomain("PayPal Support <support@paypal.com>"));
        assertEquals("evil.top", AuthHeaderValidator.extractDomain("bounce@evil.top"));
        assertEquals("", AuthHeaderValidator.extractDomain("not-an-address"));
    }
}
