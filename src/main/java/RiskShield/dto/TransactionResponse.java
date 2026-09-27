package RiskShield.dto;

import RiskShield.entity.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String type,
        String state,
        BigDecimal amount,
        String currencyCode,
        String merchantName,
        String merchantMcc,
        String merchantCity,
        String responseCode,
        String responseMemo,
        String cardLastFour,
        Instant transactionTime
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getType(),
                t.getState(),
                t.getAmount(),
                t.getCurrencyCode(),
                t.getMerchantName(),
                t.getMerchantMcc(),
                t.getMerchantCity(),
                t.getResponseCode(),
                t.getResponseMemo(),
                t.getCard() != null ? t.getCard().getLastFour() : null,
                t.getTransactionTime()
        );
    }
}