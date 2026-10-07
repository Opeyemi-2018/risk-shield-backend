package RiskShield.marqeta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarqetaSimulationResponse(RiskShield.marqeta.MarqetaTransactionEvent transaction) {
}