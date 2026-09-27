package RiskShield.marqeta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarqetaUserResponse(
        String token,
        boolean active,
        @JsonProperty("created_time") String createdTime
) {
}