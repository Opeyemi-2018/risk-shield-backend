package RiskShield.marqeta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarqetaCardResponse(
        String token,
        @JsonProperty("user_token") String userToken,
        @JsonProperty("last_four") String lastFour,
        String expiration,
        String state
) {
}