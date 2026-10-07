package RiskShield.explanation;

import java.util.UUID;

/**
 * The only information the AI ever sees. Built entirely by Java.
 * Raw response memos, risk scores, and personal data are deliberately NOT included.
 */
public record ExplanationFacts(
        UUID transactionId,
        DeclineCategory category,
        String merchantName,
        String amountText,
        String timeText,
        String cardUsedText,
        String reason,
        String nextStep
) {

    public String headline() {
        return category.headline();
    }

    public String toPromptText() {
        return """
                Category: %s
                Merchant: %s
                Amount: %s
                When: %s
                Card used: %s
                What happened: %s
                Next step: %s
                """.formatted(category.name(), merchantName, amountText, timeText,
                cardUsedText, reason, nextStep);
    }

    public String fallbackText() {
        return reason + " " + nextStep;
    }
}