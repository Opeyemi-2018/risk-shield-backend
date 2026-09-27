package RiskShield.dto;

import java.math.BigDecimal;

public record FundAccountResponse(
        String orderToken,
        BigDecimal amount,
        String currency,
        String state
) {
}