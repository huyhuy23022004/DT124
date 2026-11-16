package com.dt124.extractor;

import com.dt124.model.ParsedEmail;
import com.dt124.util.EntropyCalculator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline URL / domain analyzer reproducing the URL-side features of tyag020 (RQ1-RQ2) plus the
 * subdomain / free-hosting / anchor-mismatch signals that address paper limitations 1 and 3.
 *
 * <p>Only static strings extracted from the .eml payload are examined. No URL is ever fetched.</p>
 */
public final class UrlDomainAnalyzer {

    private static final Pattern URL_PATTERN =
            Pattern.compile("(?i)\\bhttps?://[^\\s<>\"'\\]\\)]+");

    private static final Set<String> FREE_HOSTING_SUFFIXES = Set.of(
            "web.app", "firebaseapp.com", "pages.dev", "workers.dev", "ngrok.io", "vercel.app",
            "netlify.app", "blogspot.com", "github.io", "gitbook.io", "weebly.com", "wixsite.com",
            "glitch.me", "herokuapp.com", "appspot.com", "azurewebsites.net", "sites.google.com",
            "sharepoint.com", "r2.dev", "onrender.com"
    );

    private static final Set<String> SUSPICIOUS_TLDS = Set.of(
            "xyz", "top", "icu", "vip", "shop", "click", "live", "online", "site", "club", "buzz",
            "monster", "rest", "cam", "sbs", "pw", "cc", "tk", "ml", "ga", "cf", "gq", "work",
            "fit", "link", "support", "host", "press", "space", "store", "fun", "quest", "beauty",
            "lol", "autos", "boats", "homes", "makeup", "skin"
    );

    private static final Set<String> BRANDS = Set.of(
            "paypal", "apple", "dhl", "usps", "microsoft", "netflix", "amazon", "facebook",
            "instagram", "whatsapp", "google", "dropbox", "adobe", "chase", "wellsfargo", "hsbc",
            "citibank", "linkedin", "spotify", "steam", "binance", "coinbase", "office365",
            "outlook", "fedex", "ups", "visa", "mastercard", "americanexpress", "revolut",
            "bankofamerica", "santander", "alibaba", "tiktok", "telegram"
    );

    private static final Set<String> MULTI_PART_SUFFIXES = Set.of(
            "co.uk", "org.uk", "gov.uk", "ac.uk", "com.au", "net.au", "org.au", "co.jp", "ne.jp",
            "co.in", "com.br", "com.cn", "com.sg", "com.hk", "co.za", "com.mx", "com.tr", "com.vn"
    );

    private static final Pattern IPV4 =
            Pattern.compile("^(?:\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern LABEL_DOMAIN =
            Pattern.compile("(?i)\\b((?:[a-z0-9](?:[a-z0-9\\-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24})\\b");

    public UrlAnalysis analyze(ParsedEmail email) {
        UrlAnalysis analysis = new UrlAnalysis();

        collectFromPlainText(email.getTextBody(), analysis);
        collectFromHtml(email.getHtmlBody(), analysis);

        for (String host : analysis.getHosts()) {
            evaluateHost(host, analysis);
        }
        if (!analysis.getHosts().isEmpty()) {
            analysis.setPrimaryHost(analysis.getHosts().iterator().next());
        }
        return analysis;
    }

    private void collectFromPlainText(String text, UrlAnalysis analysis) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            addUrl(cleanUrl(matcher.group()), analysis);
        }
    }

    private void collectFromHtml(String html, UrlAnalysis analysis) {
        if (html == null || html.isEmpty()) {
            return;
        }
        Document document;
        try {
            document = Jsoup.parse(html);
        } catch (Exception e) {
            collectFromPlainText(html, analysis);
            return;
        }

        Elements anchors = document.select("a[href]");
        for (Element anchor : anchors) {
            String href = anchor.attr("href").trim();
            if (href.isEmpty() || href.startsWith("#") || href.toLowerCase(Locale.ROOT).startsWith("mailto:")) {
                continue;
            }
            String normalized = cleanUrl(href);
            addUrl(normalized, analysis);
            analysis.getHtmlAnchors().add(href + " -> " + anchor.text().trim());

            String hrefHost = hostOf(normalized);
            String text = anchor.text().trim();
            String textHost = extractHostFromText(text);
            if (!hrefHost.isEmpty() && !textHost.isEmpty() && !sameRegistrableDomain(hrefHost, textHost)) {
                analysis.setHtmlAnchorMismatch(true);
            }
        }

        for (Element form : document.select("form[action]")) {
            String action = form.attr("action").trim();
            if (!action.isEmpty() && !action.startsWith("#")) {
                addUrl(cleanUrl(action), analysis);
            }
        }

        // Also catch bare URLs rendered as visible text (not wrapped in <a>).
        collectFromPlainText(document.text(), analysis);
    }

    private void addUrl(String url, UrlAnalysis analysis) {
        if (url == null || url.isEmpty()) {
            return;
        }
        analysis.getUrls().add(url);
        String host = hostOf(url);
        if (!host.isEmpty()) {
            analysis.getHosts().add(host);
        }
    }

    private void evaluateHost(String host, UrlAnalysis analysis) {
        String normalized = host.toLowerCase(Locale.ROOT);

        if (IPV4.matcher(normalized).matches()) {
            analysis.setIpUrlCount(analysis.getIpUrlCount() + 1);
        }

        if (matchesSuffix(normalized, FREE_HOSTING_SUFFIXES)) {
            analysis.setFreeHostingAbuse(true);
        }

        String tld = publicSuffix(normalized);
        if (SUSPICIOUS_TLDS.contains(tld)) {
            analysis.setSuspiciousTld(true);
        }

        String registrable = registrableDomain(normalized);
        String label = registrable.contains(".") ? registrable.substring(0, registrable.indexOf('.')) : registrable;
        if (normalized.contains("-") && containsBrand(normalized)) {
            analysis.setBrandHyphen(true);
        }

        int subdomains = subdomainDepth(normalized);
        analysis.setMaxSubdomains(Math.max(analysis.getMaxSubdomains(), subdomains));
        analysis.setMaxDomainEntropy(Math.max(analysis.getMaxDomainEntropy(),
                EntropyCalculator.shannonEntropy(label)));
    }

    public static String cleanUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        while (value.endsWith(".") || value.endsWith(",") || value.endsWith(";")
                || value.endsWith(")") || value.endsWith("]")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static String hostOf(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        String value = url.trim();
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int at = value.indexOf('@');
        if (at >= 0) {
            value = value.substring(at + 1);
        }
        int colon = value.indexOf(':');
        if (colon >= 0 && !value.startsWith("[")) {
            value = value.substring(0, colon);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    public static String extractHostFromText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String candidate = text.trim();
        Matcher urlMatcher = URL_PATTERN.matcher(candidate);
        if (urlMatcher.find()) {
            return hostOf(cleanUrl(urlMatcher.group()));
        }
        Matcher domainMatcher = LABEL_DOMAIN.matcher(candidate);
        if (domainMatcher.find()) {
            return domainMatcher.group(1).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    public static String registrableDomain(String host) {
        if (host == null || host.isEmpty() || IPV4.matcher(host).matches()) {
            return host == null ? "" : host;
        }
        String[] labels = host.split("\\.");
        if (labels.length <= 2) {
            return host;
        }
        String lastTwo = labels[labels.length - 2] + "." + labels[labels.length - 1];
        if (MULTI_PART_SUFFIXES.contains(lastTwo) && labels.length >= 3) {
            return labels[labels.length - 3] + "." + lastTwo;
        }
        return lastTwo;
    }

    public static String publicSuffix(String host) {
        if (host == null || host.isEmpty() || IPV4.matcher(host).matches()) {
            return "";
        }
        String[] labels = host.split("\\.");
        if (labels.length < 2) {
            return "";
        }
        String lastTwo = labels[labels.length - 2] + "." + labels[labels.length - 1];
        if (MULTI_PART_SUFFIXES.contains(lastTwo)) {
            return lastTwo;
        }
        return labels[labels.length - 1];
    }

    public static int subdomainDepth(String host) {
        if (host == null || host.isEmpty() || IPV4.matcher(host).matches()) {
            return 0;
        }
        String registrable = registrableDomain(host);
        if (registrable.equals(host)) {
            return 0;
        }
        int extra = host.substring(0, host.length() - registrable.length() - 1).length();
        if (extra <= 0) {
            return 0;
        }
        return host.substring(0, host.length() - registrable.length() - 1).split("\\.").length;
    }

    public static boolean sameRegistrableDomain(String a, String b) {
        return registrableDomain(a).equalsIgnoreCase(registrableDomain(b));
    }

    private static boolean matchesSuffix(String host, Set<String> suffixes) {
        for (String suffix : suffixes) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsBrand(String host) {
        for (String brand : BRANDS) {
            if (host.contains(brand)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBrand(String token) {
        return BRANDS.contains(token.toLowerCase(Locale.ROOT));
    }

    public static Set<String> brands() {
        return BRANDS;
    }

    public static Set<String> freeHostingSuffixes() {
        return FREE_HOSTING_SUFFIXES;
    }

    public static Set<String> suspiciousTlds() {
        return SUSPICIOUS_TLDS;
    }
}
