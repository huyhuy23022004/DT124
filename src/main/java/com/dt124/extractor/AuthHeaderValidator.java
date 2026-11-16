package com.dt124.extractor;

import com.dt124.model.AuthResult;
import com.dt124.model.ParsedEmail;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts sender identity fields and authentication results (SPF / DKIM / DMARC) from the parsed
 * headers. This addresses paper limitation 2 (missing delivery context) purely offline.
 */
public final class AuthHeaderValidator {

    private static final Pattern ADDRESS_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+\\-]+@([A-Za-z0-9.\\-]+\\.[A-Za-z]{2,})");

    private static final Set<String> FREE_MAIL_DOMAINS = Set.of(
            "gmail.com", "googlemail.com", "yahoo.com", "outlook.com", "hotmail.com",
            "live.com", "aol.com", "protonmail.com", "mail.com", "gmx.com", "yandex.com"
    );

    public AuthResult validate(ParsedEmail email) {
        AuthResult result = new AuthResult();

        result.setFromAddress(email.getFrom());
        result.setReturnPath(email.getReturnPath());
        result.setReplyTo(email.getReplyTo());
        result.setTo(email.getTo());
        result.setSubject(email.getSubject());
        result.setDate(email.getDate());
        result.setReceivedHops(email.getReceivedHeaders().size());

        result.setHasAuthenticationResults(!email.getAuthResults().isEmpty());
        result.setHasDkimSignature(!email.getDkimSignature().isEmpty());
        result.setHasReceivedSpf(!email.getReceivedSpf().isEmpty());

        result.setSpf(resolveSpf(email));
        result.setDkim(resolveDkim(email));
        result.setDmarc(resolveMechanism(email.getAuthResults(), "dmarc"));

        String fromDomain = extractDomain(email.getFrom());
        String returnPathDomain = extractDomain(email.getReturnPath());
        String replyToDomain = extractDomain(email.getReplyTo());

        result.setFromReturnPathMismatch(!fromDomain.isEmpty() && !returnPathDomain.isEmpty()
                && !fromDomain.equalsIgnoreCase(returnPathDomain));
        result.setFromReplyToMismatch(!fromDomain.isEmpty() && !replyToDomain.isEmpty()
                && !fromDomain.equalsIgnoreCase(replyToDomain));
        result.setReplyToFreeMail(FREE_MAIL_DOMAINS.contains(replyToDomain));
        return result;
    }

    private AuthResult.Status resolveSpf(ParsedEmail email) {
        AuthResult.Status status = resolveMechanism(email.getAuthResults(), "spf");
        if (status != AuthResult.Status.MISSING) {
            return status;
        }
        String receivedSpf = email.getReceivedSpf().trim();
        if (receivedSpf.isEmpty()) {
            return AuthResult.Status.MISSING;
        }
        String token = receivedSpf.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return mapStatus(token);
    }

    private AuthResult.Status resolveDkim(ParsedEmail email) {
        AuthResult.Status status = resolveMechanism(email.getAuthResults(), "dkim");
        if (status != AuthResult.Status.MISSING) {
            return status;
        }
        // A DKIM-Signature header may exist while Authentication-Results is absent.
        return email.getDkimSignature().isEmpty() ? AuthResult.Status.MISSING : AuthResult.Status.NONE;
    }

    private AuthResult.Status resolveMechanism(String source, String mechanism) {
        if (source == null || source.isEmpty()) {
            return AuthResult.Status.MISSING;
        }
        Pattern pattern = Pattern.compile("(?i)\\b" + Pattern.quote(mechanism)
                + "\\s*=\\s*(pass|fail|softfail|neutral|none|temperror|permerror)");
        Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            return mapStatus(matcher.group(1));
        }
        return AuthResult.Status.MISSING;
    }

    private AuthResult.Status mapStatus(String token) {
        return switch (token == null ? "" : token.toLowerCase(Locale.ROOT)) {
            case "pass" -> AuthResult.Status.PASS;
            case "fail", "permerror" -> AuthResult.Status.FAIL;
            case "softfail", "temperror" -> AuthResult.Status.SOFTFAIL;
            case "neutral" -> AuthResult.Status.NEUTRAL;
            case "none" -> AuthResult.Status.NONE;
            default -> AuthResult.Status.MISSING;
        };
    }

    public static String extractDomain(String address) {
        if (address == null || address.isEmpty()) {
            return "";
        }
        Matcher matcher = ADDRESS_PATTERN.matcher(address);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    public static boolean isFreeMailDomain(String domain) {
        return domain != null && FREE_MAIL_DOMAINS.contains(domain.toLowerCase(Locale.ROOT));
    }

    public static List<String> freeMailDomains() {
        return List.copyOf(FREE_MAIL_DOMAINS);
    }
}
