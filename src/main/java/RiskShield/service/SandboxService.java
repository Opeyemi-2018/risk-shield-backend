package RiskShield.service;

import RiskShield.analysis.InsightService;
import RiskShield.dto.SeedHistoryRequest;
import RiskShield.dto.SeedHistoryResponse;
import RiskShield.dto.SimulatePurchaseRequest;
import RiskShield.dto.SimulationResultResponse;
import RiskShield.entity.Card;
import RiskShield.entity.CardStatus;
import RiskShield.entity.Transaction;
import RiskShield.entity.User;
import RiskShield.entity.UserStatus;
import RiskShield.exception.*;
import RiskShield.marqeta.MarqetaClient;
import RiskShield.marqeta.MarqetaSimulationRequest;
import RiskShield.marqeta.MarqetaSimulationResponse;
import RiskShield.repository.CardRepository;
import RiskShield.repository.TransactionRepository;
import RiskShield.repository.UserRepository;
import RiskShield.sandbox.SyntheticHistoryGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "riskshield.sandbox.enabled", havingValue = "true")
public class SandboxService {

    private final UserRepository userRepository;
    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;
    private final MarqetaClient marqetaClient;
    private final SyntheticHistoryGenerator syntheticHistoryGenerator;
    private final InsightService insightService;

    public SimulationResultResponse simulatePurchase(UUID userId, SimulatePurchaseRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getUserStatus() != UserStatus.ACTIVE) {
            throw new UserNotActiveException(userId);
        }

        Card card = cardRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, CardStatus.ACTIVE)
                .orElseThrow(() -> new NoActiveCardException(userId));

        String mid = request.mid() != null && !request.mid().isBlank()
                ? request.mid()
                : defaultMid(request.merchantName());

        MarqetaSimulationRequest simulation = MarqetaSimulationRequest.authorization(
                card.getMarqetaCardToken(),
                request.amount(),
                new MarqetaSimulationRequest.CardAcceptor(
                        mid, request.mcc(), request.merchantName().trim(), request.city()));

        MarqetaSimulationResponse response = call(() -> marqetaClient.simulateAuthorization(simulation),
                "Purchase simulation failed");

        log.info("Simulated purchase for user {}: {} {} -> {}",
                userId, request.amount(), request.merchantName(), response.transaction().state());
        return SimulationResultResponse.from(response.transaction());
    }

    public SimulationResultResponse simulateClearing(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        if (!"authorization".equals(transaction.getType()) || !"PENDING".equals(transaction.getState())) {
            throw new InvalidTransactionStateException(
                    "Only pending authorizations can be cleared. This transaction is "
                            + transaction.getType() + " / " + transaction.getState() + ".");
        }

        MarqetaSimulationRequest simulation = MarqetaSimulationRequest.clearing(
                transaction.getMarqetaToken(), transaction.getAmount());

        MarqetaSimulationResponse response = call(() -> marqetaClient.simulateClearing(simulation),
                "Clearing simulation failed");

        log.info("Simulated clearing for transaction {} -> {}", transactionId, response.transaction().state());
        return SimulationResultResponse.from(response.transaction());
    }

    public SeedHistoryResponse seedHistory(UUID userId, SeedHistoryRequest request) {
        SeedHistoryResponse response = syntheticHistoryGenerator.generate(userId, request);
        insightService.recalculateForUser(userId);
        return response;
    }

    private MarqetaSimulationResponse call(Supplier<MarqetaSimulationResponse> action, String errorMessage) {
        try {
            MarqetaSimulationResponse response = action.get();
            if (response == null || response.transaction() == null) {
                throw new MarqetaIntegrationException(errorMessage + ": empty response", null);
            }
            return response;
        } catch (RestClientException ex) {
            log.error("{}: {}", errorMessage, ex.getMessage());
            throw new MarqetaIntegrationException(errorMessage, ex);
        }
    }

    private String defaultMid(String merchantName) {
        String digits = String.valueOf(Math.abs(merchantName.trim().toLowerCase().hashCode()));
        return digits.length() > 15 ? digits.substring(0, 15) : digits;
    }
}