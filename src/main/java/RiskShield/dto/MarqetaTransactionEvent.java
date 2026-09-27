package RiskShield.marqeta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarqetaTransactionEvent(
        String token,
        String type,
        String state,
        @JsonProperty("user_token") String userToken,
        @JsonProperty("card_token") String cardToken,
        BigDecimal amount,
        @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("user_transaction_time") String userTransactionTime,
        @JsonProperty("created_time") String createdTime,
        @JsonProperty("preceding_related_transaction_token") String precedingRelatedTransactionToken,
        Response response,
        Gpa gpa,
        Fraud fraud,
        @JsonProperty("card_acceptor") CardAcceptor cardAcceptor
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String code, String memo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Gpa(@JsonProperty("available_balance") BigDecimal availableBalance) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fraud(Network network) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Network(@JsonProperty("transaction_risk_score") Integer transactionRiskScore) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CardAcceptor(
            String mid,
            String mcc,
            String name,
            String city,
            @JsonProperty("country_code") String countryCode
    ) {
    }
}