package com.dt124.extractor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight YARA-style safe indicator engine. It reads a rules file (see
 * {@code rules/safe_indicators.yar}) containing rules of the form:
 *
 * <pre>
 * rule eval_obfuscation
 * {
 *     strings:
 *         $a = "eval(" nocase
 *         $b = /document\s*\.\s*write/
 *     condition:
 *         any of them
 * }
 * </pre>
 *
 * Rule matching is performed entirely in RAM against headers, bodies, URLs and attachment metadata.
 * No payload is executed or written to disk. If the rules file is absent, a built-in default rule
 * set (identical to the shipped file) is used so the pipeline never fails.
 */
public final class SafeIndicatorRuleEngine {

    public static final List<String> PRIMARY_RULES = List.of(
            "eval_obfuscation", "base64_blob", "hidden_login_form", "onload_js"
    );

    private final List<Rule> rules = new ArrayList<>();
    private final Path rulesFile;

    public SafeIndicatorRuleEngine(Path rulesFile) {
        this.rulesFile = rulesFile;
        load();
    }

    public SafeIndicatorRuleEngine() {
        this(null);
    }

    public Path getRulesFile() {
        return rulesFile;
    }

    public List<String> ruleNames() {
        List<String> names = new ArrayList<>();
        for (Rule rule : rules) {
            names.add(rule.name);
        }
        return names;
    }

    private void load() {
        String content = null;
        if (rulesFile != null && Files.isRegularFile(rulesFile)) {
            try {
                content = Files.readString(rulesFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                content = null;
            }
        }
        if (content == null) {
            content = DEFAULT_RULES;
        }
        parse(content);
        if (rules.isEmpty()) {
            parse(DEFAULT_RULES);
        }
    }

    private void parse(String content) {
        String[] lines = content.split("\\r?\\n");
        Rule current = null;
        boolean inStrings = false;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) {
                continue;
            }
            if (line.toLowerCase(Locale.ROOT).startsWith("rule ")) {
                String name = line.substring(5).trim();
                int brace = name.indexOf('{');
                if (brace >= 0) {
                    name = name.substring(0, brace).trim();
                }
                current = new Rule(name);
                inStrings = false;
                continue;
            }
            if (current == null) {
                continue;
            }
            if (line.startsWith("{")) {
                continue;
            }
            if (line.equals("}")) {
                if (!current.patterns.isEmpty()) {
                    rules.add(current);
                }
                current = null;
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("strings:")) {
                inStrings = true;
                continue;
            }
            if (lower.startsWith("condition:")) {
                inStrings = false;
                current.condition = line.substring("condition:".length()).trim();
                continue;
            }
            if (inStrings && line.startsWith("$")) {
                parseString(line, current);
            }
        }
    }

    private void parseString(String line, Rule rule) {
        int eq = line.indexOf('=');
        if (eq < 0) {
            return;
        }
        String value = line.substring(eq + 1).trim();
        boolean nocase = value.toLowerCase(Locale.ROOT).endsWith("nocase");
        if (nocase) {
            value = value.substring(0, value.toLowerCase(Locale.ROOT).lastIndexOf("nocase")).trim();
        }
        Pattern pattern;
        if (value.startsWith("/")) {
            int lastSlash = value.lastIndexOf('/');
            if (lastSlash <= 0) {
                return;
            }
            String regex = value.substring(1, lastSlash);
            String flags = value.substring(lastSlash + 1).trim();
            int flagBits = 0;
            if (flags.contains("i") || nocase) {
                flagBits |= Pattern.CASE_INSENSITIVE;
            }
            if (flags.contains("s")) {
                flagBits |= Pattern.DOTALL;
            }
            if (flags.contains("m")) {
                flagBits |= Pattern.MULTILINE;
            }
            pattern = Pattern.compile(regex, flagBits);
        } else {
            String literal = value;
            if (literal.startsWith("\"") && literal.endsWith("\"") && literal.length() >= 2) {
                literal = literal.substring(1, literal.length() - 1);
            }
            pattern = Pattern.compile(Pattern.quote(literal), nocase ? Pattern.CASE_INSENSITIVE : 0);
        }
        rule.patterns.add(pattern);
    }

    /**
     * Scans the given text and returns per-rule hit counts.
     */
    public IndicatorAnalysis scan(String text) {
        IndicatorAnalysis analysis = new IndicatorAnalysis();
        String haystack = text == null ? "" : text;
        for (Rule rule : rules) {
            int matched = 0;
            for (Pattern pattern : rule.patterns) {
                Matcher matcher = pattern.matcher(haystack);
                if (matcher.find()) {
                    matched++;
                }
            }
            int required = requiredMatches(rule);
            if (matched >= required && matched > 0) {
                analysis.put(rule.name, matched);
            } else {
                analysis.put(rule.name, 0);
            }
        }
        return analysis;
    }

    private int requiredMatches(Rule rule) {
        String condition = rule.condition == null ? "any of them" : rule.condition.toLowerCase(Locale.ROOT);
        if (condition.contains("all of them")) {
            return Math.max(1, rule.patterns.size());
        }
        Matcher countMatcher = Pattern.compile("(\\d+)\\s+of\\s+them").matcher(condition);
        if (countMatcher.find()) {
            return Integer.parseInt(countMatcher.group(1));
        }
        return 1;
    }

    private static final class Rule {
        private final String name;
        private final List<Pattern> patterns = new ArrayList<>();
        private String condition = "any of them";

        private Rule(String name) {
            this.name = name;
        }
    }

    /**
     * Built-in default rule set, identical in content to rules/safe_indicators.yar.
     */
    private static final String DEFAULT_RULES = """
            rule eval_obfuscation
            {
                strings:
                    $a = "eval(" nocase
                    $b = /document\\s*\\.\\s*write/
                    $c = "unescape(" nocase
                    $d = /String\\.fromCharCode/
                    $e = /atob\\s*\\(/
                condition:
                    any of them
            }

            rule base64_blob
            {
                strings:
                    $blob = /[A-Za-z0-9+\\/]{120,}={0,2}/
                condition:
                    any of them
            }

            rule hidden_login_form
            {
                strings:
                    $pw = /<input[^>]*type\\s*=\\s*["']?password/
                    $hidden = /display\\s*:\\s*none|visibility\\s*:\\s*hidden/
                condition:
                    all of them
            }

            rule onload_js
            {
                strings:
                    $onload = /on(load|error|mouseover)\\s*=/
                    $script = /<script/i
                condition:
                    any of them
            }

            rule credential_harvest
            {
                strings:
                    $a = /verify your account/i
                    $b = /confirm your (account|identity|information)/i
                    $c = /update your (payment|billing|account)/i
                    $d = /unusual sign.?in/i
                    $e = /account (has been )?(suspended|locked|limited)/i
                condition:
                    any of them
            }

            rule urgent_language
            {
                strings:
                    $a = /within 24 hours/i
                    $b = /immediate action/i
                    $c = /final (warning|notice)/i
                    $d = /your account will be (closed|suspended|terminated)/i
                condition:
                    any of them
            }

            rule remote_template
            {
                strings:
                    $iframe = /<iframe/i
                    $embed = /<embed/i
                    $object = /<object/i
                    $datauri = /src\\s*=\\s*["']?data:/
                condition:
                    any of them
            }

            rule crypto_wallet
            {
                strings:
                    $btc = /\\b(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,39}\\b/
                    $eth = /0x[a-fA-F0-9]{40}/
                    $kw = /\\b(bitcoin|usdt|wallet address|seed phrase)\\b/i
                condition:
                    any of them
            }
            """;
}
