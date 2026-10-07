package RiskShield.explanation;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Classifies a declined transaction into a DeclineCategory.
 * Known Marqeta response codes are matched first; otherwise the response memo is
 * matched by keywords. SECURITY is checked first on purpose: if anything looks
 * fraud-related, the explanation stays vague.
 */
@Component
public class DeclineCatalog {

    // Confirmed from real sandbox data. Add more codes here from Marqeta's
    // "transaction response codes" documentation as you encounter them.
    private static final Map<String, DeclineCategory> KNOWN_CODES = Map.of(
            "1919", DeclineCategory.CARD_INACTIVE   // "Card terminated"
    );

    public DeclineCategory classify(String responseCode, String responseMemo) {
        if (responseCode != null && KNOWN_CODES.containsKey(responseCode)) {
            return KNOWN_CODES.get(responseCode);
        }

        String memo = responseMemo == null ? "" : responseMemo.toLowerCase(Locale.ROOT);

        if (containsAny(memo, "fraud", "suspect", "risk", "security", "blocked")) {
            return DeclineCategory.SECURITY;
        }
        if (containsAny(memo, "insufficient", "not sufficient", "nsf")) {
            return DeclineCategory.INSUFFICIENT_FUNDS;
        }
        if (containsAny(memo, "expired")) {
            return DeclineCategory.CARD_EXPIRED;
        }
        if (containsAny(memo, "terminated", "suspended", "not active", "inactive", "unactivated", "closed")) {
            return DeclineCategory.CARD_INACTIVE;
        }
        if (containsAny(memo, "limit", "velocity", "exceed")) {
            return DeclineCategory.SPENDING_LIMIT;
        }
        if (containsAny(memo, "cvv", "pin", "invalid card", "incorrect")) {
            return DeclineCategory.INVALID_CARD_DETAILS;
        }
        if (containsAny(memo, "merchant", "mcc", "not permitted", "not allowed")) {
            return DeclineCategory.MERCHANT_NOT_ALLOWED;
        }
        if (containsAny(memo, "timeout", "unavailable", "system error", "processing error")) {
            return DeclineCategory.TECHNICAL;
        }
        return DeclineCategory.UNKNOWN;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}