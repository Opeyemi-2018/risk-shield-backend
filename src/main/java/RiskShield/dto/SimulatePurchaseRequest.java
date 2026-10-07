package RiskShield.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record SimulatePurchaseRequest(

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @DecimalMax(value = "10000.00", message = "Amount must not exceed 10000.00")
        @Digits(integer = 5, fraction = 2, message = "Amount can have at most 2 decimal places")
        BigDecimal amount,

        @NotBlank(message = "Merchant name is required")
        @Size(max = 100, message = "Merchant name must be at most 100 characters")
        String merchantName,

        @NotBlank(message = "MCC is required")
        @Pattern(regexp = "\\d{4}", message = "MCC must be 4 digits")
        String mcc,

        @Size(min = 2, max = 15, message = "MID must be 2–15 characters")
        String mid,

        @Size(max = 100, message = "City must be at most 100 characters")
        String city
) {
}