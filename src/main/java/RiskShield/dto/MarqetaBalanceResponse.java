package RiskShield.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarqetaBalanceResponse(Gpa gpa) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Gpa(
            @JsonProperty("available_balance") BigDecimal availableBalance,
            @JsonProperty("ledger_balance") BigDecimal ledgerBalance,
            @JsonProperty("currency_code") String currencyCode
    ) {
    }
}