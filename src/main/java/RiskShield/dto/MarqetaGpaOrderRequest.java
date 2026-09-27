package RiskShield.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record MarqetaGpaOrderRequest(
        String token,
        @JsonProperty("user_token") String userToken,
        BigDecimal amount,
        @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("funding_source_token") String fundingSourceToken
) {
}