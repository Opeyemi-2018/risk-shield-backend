package RiskShield.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarqetaGpaOrderResponse(
        String token,
        BigDecimal amount,
        @JsonProperty("currency_code") String currencyCode,
        String state
) {
}