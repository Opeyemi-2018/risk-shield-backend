package RiskShield.dto;

import RiskShield.analysis.InsightLevel;
import RiskShield.analysis.InsightSignal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionInsightResponse(
        UUID transactionId,
        String merchantName,
        String categoryLabel,
        BigDecimal amount,
        String currencyCode,
        Instant transactionTime,
        boolean synthetic,
        int score,
        InsightLevel level,
        List<InsightSignal> signals,
        Instant evaluatedAt
) {
}