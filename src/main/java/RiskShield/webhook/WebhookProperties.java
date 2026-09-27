package RiskShield.webhook;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "webhook")
public record WebhookProperties(
        @NotBlank String username,
        @NotBlank String password
) {
}