package RiskShield.dto;

import RiskShield.marqeta.MarqetaTransactionEvent;

import java.math.BigDecimal;

public record SimulationResultResponse(
        String type,
        String state,
        BigDecimal amount,
        String responseCode,
        String responseMemo,
        String merchantName,
        BigDecimal availableBalance
) {
    public static SimulationResultResponse from(MarqetaTransactionEvent t) {
        return new SimulationResultResponse(
                t.type(),
                t.state(),
                t.amount(),
                t.response() != null ? t.response().code() : null,
                t.response() != null ? t.response().memo() : null,
                t.cardAcceptor() != null ? t.cardAcceptor().name() : null,
                t.gpa() != null ? t.gpa().availableBalance() : null
        );
    }
}