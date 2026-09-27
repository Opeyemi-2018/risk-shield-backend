package RiskShield.marqeta;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "marqeta")
public record MarqetaProperties(
        @NotBlank String baseUrl,
        @NotBlank String applicationToken,
        @NotBlank String adminAccessToken,
        @NotBlank String cardProductToken,
        @NotBlank String fundingSourceToken
) {
}