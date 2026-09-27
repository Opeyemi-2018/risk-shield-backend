package RiskShield.dto;

import java.math.BigDecimal;

public record BalanceResponse(
        BigDecimal availableBalance,
        BigDecimal ledgerBalance,
        String currency
) {
}