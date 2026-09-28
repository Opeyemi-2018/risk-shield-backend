package RiskShield.marqeta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarqetaCardTransitionEvent(
        String token,
        String type,
        String state,
        String reason,
        @JsonProperty("reason_code") String reasonCode,
        String channel,
        @JsonProperty("card_token") String cardToken,
        @JsonProperty("user_token") String userToken,
        @JsonProperty("created_time") String createdTime,
        @JsonProperty("created_timestamp") String createdTimestamp   // NEW
) {
}