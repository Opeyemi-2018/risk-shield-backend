package RiskShield.marqeta;

import RiskShield.dto.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MarqetaClient {

    private final RestClient restClient;

    public MarqetaClient(@Qualifier("marqetaRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public String ping() {
        return restClient.get()
                .uri("/ping")
                .retrieve()
                .body(String.class);
    }

    public MarqetaUserResponse createUser(MarqetaCreateUserRequest request) {
        return restClient.post()
                .uri("/users")
                .body(request)
                .retrieve()
                .body(MarqetaUserResponse.class);
    }

    public MarqetaCardResponse createCard(MarqetaCreateCardRequest request) {
        return restClient.post()
                .uri("/cards")
                .body(request)
                .retrieve()
                .body(MarqetaCardResponse.class);
    }

    public MarqetaGpaOrderResponse createGpaOrder(MarqetaGpaOrderRequest request) {
        return restClient.post()
                .uri("/gpaorders")
                .body(request)
                .retrieve()
                .body(MarqetaGpaOrderResponse.class);
    }

    public MarqetaBalanceResponse getBalance(String userToken) {
        return restClient.get()
                .uri("/balances/{userToken}", userToken)
                .retrieve()
                .body(MarqetaBalanceResponse.class);
    }

    public MarqetaSimulationResponse simulateAuthorization(MarqetaSimulationRequest request) {
        return restClient.post()
                .uri("/simulations/cardtransactions/authorization")
                .body(request)
                .retrieve()
                .body(MarqetaSimulationResponse.class);
    }

    public MarqetaSimulationResponse simulateClearing(MarqetaSimulationRequest request) {
        return restClient.post()
                .uri("/simulations/cardtransactions/authorization.clearing")
                .body(request)
                .retrieve()
                .body(MarqetaSimulationResponse.class);
    }
}
