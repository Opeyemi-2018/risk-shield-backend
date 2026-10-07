package RiskShield.marqeta;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MarqetaSimulationRequest(
        @JsonProperty("card_token") String cardToken,
        BigDecimal amount,
        @JsonProperty("card_acceptor") CardAcceptor cardAcceptor,
        @JsonProperty("preceding_related_transaction_token") String precedingRelatedTransactionToken
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CardAcceptor(String mid, String mcc, String name, String city) {
    }

    public static MarqetaSimulationRequest authorization(String cardToken, BigDecimal amount, CardAcceptor acceptor) {
        return new MarqetaSimulationRequest(cardToken, amount, acceptor, null);
    }

    public static MarqetaSimulationRequest clearing(String authorizationToken, BigDecimal amount) {
        return new MarqetaSimulationRequest(null, amount, null, authorizationToken);
    }
}