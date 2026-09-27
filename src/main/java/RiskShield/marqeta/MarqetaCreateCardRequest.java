package RiskShield.marqeta;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MarqetaCreateCardRequest(
        @JsonProperty("user_token") String userToken,
        @JsonProperty("card_product_token") String cardProductToken
) {
}