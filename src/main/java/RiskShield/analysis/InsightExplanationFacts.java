package RiskShield.analysis;

import RiskShield.entity.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The only information the AI sees when explaining an unusual purchase. Built by Java. */
public record InsightExplanationFacts(
        UUID transactionId,
        InsightLevel level,
        int score,
        String merchantName,
        String categoryLabel,
        String amountText,
        String timeText,
        List<String> reasons,
        String nextStep
) {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'WAT'").withZone(ZoneId.of("Africa/Lagos"));

    public static InsightExplanationFacts of(Transaction tx, int score, InsightLevel level,
                                             List<InsightSignal> signals) {
        List<String> reasons = signals.stream()
                .sorted(Comparator.comparingInt(InsightSignal::points).reversed())  // strongest first
                .map(InsightSignal::message)
                .toList();

        return new InsightExplanationFacts(
                tx.getId(),
                level,
                score,
                Objects.toString(tx.getMerchantName(), "the merchant"),
                MerchantCategories.label(tx.getMerchantMcc()),
                money(tx.getAmount()),
                TIME_FORMAT.format(tx.getTransactionTime()),
                reasons,
                nextStepFor(level)
        );
    }

    public boolean needsExplanation() {
        return level == InsightLevel.UNUSUAL || level == InsightLevel.HIGH;
    }

    public String headline() {
        return switch (level) {
            case HIGH -> "Very unusual for you";
            case UNUSUAL -> "A bit unusual for you";
            case NORMAL -> "Looks normal";
            case NOT_ENOUGH_HISTORY -> "Not enough history yet";
        };
    }

    public String toPromptText() {
        StringBuilder text = new StringBuilder()
                .append("Level: ").append(level.name()).append('\n')
                .append("Merchant: ").append(merchantName).append('\n')
                .append("Category: ").append(categoryLabel).append('\n')
                .append("Amount: ").append(amountText).append('\n')
                .append("When: ").append(timeText).append('\n')
                .append("Reasons it stood out:\n");
        for (String reason : reasons) {
            text.append("- ").append(reason).append('\n');
        }
        text.append("Next step: ").append(nextStep).append('\n');
        return text.toString();
    }

    public String fallbackText() {
        return switch (level) {
            case HIGH, UNUSUAL -> "Your " + amountText + " purchase at " + merchantName + " stood out. "
                    + String.join(" ", reasons) + " " + nextStep;
            case NORMAL -> "This purchase looks normal compared with your usual spending.";
            case NOT_ENOUGH_HISTORY -> "There isn't enough spending history yet to compare this purchase with.";
        };
    }

    private static String nextStepFor(InsightLevel level) {
        return switch (level) {
            case HIGH -> "If you don't recognise this purchase, contact support right away so your card "
                    + "can be frozen. If it was you, you don't need to do anything.";
            case UNUSUAL -> "If this was you, there's nothing you need to do. "
                    + "If you don't recognise it, contact support.";
            case NORMAL -> "No action needed.";
            case NOT_ENOUGH_HISTORY -> "No action needed. RiskShield will learn your habits as you use your card.";
        };
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "an unknown amount"
                : "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}