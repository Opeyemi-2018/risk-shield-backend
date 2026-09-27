package RiskShield.marqeta;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MarqetaCreateUserRequest(
        String token,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String email,
        String phone
) {
}