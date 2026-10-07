package RiskShield.explanation;

import RiskShield.entity.Card;
import RiskShield.entity.CardStatus;
import RiskShield.entity.Transaction;
import RiskShield.exception.InvalidTransactionStateException;
import RiskShield.exception.TransactionNotFoundException;
import RiskShield.repository.CardRepository;
import RiskShield.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExplanationFactBuilder {

    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'WAT'").withZone(LAGOS);

    private final TransactionRepository transactionRepository;
    private final CardRepository cardRepository;
    private final DeclineCatalog declineCatalog;

    private record Resolution(DeclineCategory category, String reason, String nextStep) {
    }

    @Transactional(readOnly = true)
    public ExplanationFacts build(UUID userId, UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        // A user may only see explanations for their own transactions.
        // Answer "not found" rather than "forbidden", so we don't confirm it exists.
        if (!transaction.getUser().getId().equals(userId)) {
            throw new TransactionNotFoundException(transactionId);
        }

        if (!"DECLINED".equals(transaction.getState())) {
            throw new InvalidTransactionStateException(
                    "Explanations are currently available for declined transactions only.");
        }

        Card usedCard = transaction.getCard();
        Card activeCard = cardRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, CardStatus.ACTIVE)
                .orElse(null);

        DeclineCategory category = declineCatalog.classify(
                transaction.getResponseCode(), transaction.getResponseMemo());
        Resolution resolution = resolve(category, transaction, usedCard, activeCard);

        return new ExplanationFacts(
                transaction.getId(),
                resolution.category(),
                transaction.getMerchantName() != null ? transaction.getMerchantName() : "the merchant",
                money(transaction.getAmount(), transaction.getCurrencyCode()),
                TIME_FORMAT.format(transaction.getTransactionTime()),
                usedCard != null ? "ending in " + usedCard.getLastFour() : "unknown",
                resolution.reason(),
                resolution.nextStep()
        );
    }

    private Resolution resolve(DeclineCategory category, Transaction t, Card used, Card active) {
        return switch (category) {
            case CARD_INACTIVE, CARD_REPLACED -> resolveInactiveCard(used, active);
            case INSUFFICIENT_FUNDS -> resolveInsufficientFunds(t);
            case CARD_EXPIRED -> new Resolution(category,
                    "The card used has expired.",
                    active != null ? "Use your card ending in " + active.getLastFour() + " instead."
                            : "Issue a new card from your dashboard, then try again.");
            case SPENDING_LIMIT -> new Resolution(category,
                    "This purchase went over a spending limit on your card.",
                    "Try a smaller amount, or wait for the limit to reset.");
            case INVALID_CARD_DETAILS -> new Resolution(category,
                    "The card details entered didn't match our records.",
                    "Check the card number, expiry date, and security code, then try again.");
            case MERCHANT_NOT_ALLOWED -> new Resolution(category,
                    "This card can't be used for this type of merchant.",
                    "Use a different payment method for this purchase.");
            case SECURITY -> new Resolution(category,
                    "This purchase was declined to help protect your account.",
                    "If this was you, contact support to confirm it. If it wasn't you, freeze your card.");
            case TECHNICAL -> new Resolution(category,
                    "There was a temporary problem processing this purchase.",
                    "Please try again in a few minutes.");
            case UNKNOWN -> new Resolution(category,
                    "The card issuer declined this purchase.",
                    "Contact support for more details.");
        };
    }

    private Resolution resolveInactiveCard(Card used, Card active) {
        String usedText = used != null ? "card ending in " + used.getLastFour() : "card used";
        boolean hasOtherActiveCard = active != null && (used == null || !active.getId().equals(used.getId()));

        if (used != null && used.getStatus() == CardStatus.TERMINATED && hasOtherActiveCard) {
            return new Resolution(DeclineCategory.CARD_REPLACED,
                    "Your " + usedText + " was replaced by a newer card and can no longer be used.",
                    "Use your card ending in " + active.getLastFour() + " instead.");
        }
        if (used != null && used.getStatus() == CardStatus.SUSPENDED) {
            return new Resolution(DeclineCategory.CARD_INACTIVE,
                    "Your " + usedText + " is currently suspended.",
                    "Contact support to reactivate it.");
        }
        if (hasOtherActiveCard) {
            return new Resolution(DeclineCategory.CARD_INACTIVE,
                    "Your " + usedText + " is no longer active.",
                    "Use your card ending in " + active.getLastFour() + " instead.");
        }
        return new Resolution(DeclineCategory.CARD_INACTIVE,
                "Your " + usedText + " is not active.",
                "Issue a new card from your dashboard, then try again.");
    }

    private Resolution resolveInsufficientFunds(Transaction t) {
        BigDecimal available = t.getAvailableBalanceAfter();
        BigDecimal amount = t.getAmount();

        if (available != null && amount != null && available.compareTo(amount) < 0) {
            BigDecimal shortfall = amount.subtract(available);
            return new Resolution(DeclineCategory.INSUFFICIENT_FUNDS,
                    "Your available balance was " + money(available, t.getCurrencyCode())
                            + ", but the purchase was " + money(amount, t.getCurrencyCode()) + ".",
                    "Add at least " + money(shortfall, t.getCurrencyCode()) + " to your account and try again.");
        }
        return new Resolution(DeclineCategory.INSUFFICIENT_FUNDS,
                "Your available balance wasn't enough for this purchase.",
                "Add funds to your account and try again.");
    }

    private String money(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            return "an unknown amount";
        }
        String value = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        return "USD".equals(currencyCode) || currencyCode == null ? "$" + value : value + " " + currencyCode;
    }
}