package RiskShield.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record FundAccountRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
        @DecimalMax(value = "10000.00", message = "Amount must not exceed 10000.00")
        @Digits(integer = 5, fraction = 2, message = "Amount can have at most 2 decimal places")
        BigDecimal amount
) {
}