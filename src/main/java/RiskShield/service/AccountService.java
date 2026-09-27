package RiskShield.service;

import RiskShield.dto.*;
import RiskShield.entity.User;
import RiskShield.entity.UserStatus;
import RiskShield.exception.MarqetaIntegrationException;
import RiskShield.exception.UserNotActiveException;
import RiskShield.exception.UserNotFoundException;
import RiskShield.marqeta.*;
import RiskShield.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String CURRENCY = "USD";

    private final UserRepository userRepository;
    private final MarqetaClient marqetaClient;
    private final MarqetaProperties marqetaProperties;

    public FundAccountResponse fundAccount(UUID userId, FundAccountRequest request) {
        User user = getActiveUser(userId);

        try {
            RiskShield.dto.MarqetaGpaOrderResponse order = marqetaClient.createGpaOrder(new MarqetaGpaOrderRequest(
                    UUID.randomUUID().toString(),
                    user.getMarqetaUserToken(),
                    request.amount(),
                    CURRENCY,
                    marqetaProperties.fundingSourceToken()
            ));
            log.info("Funded user {} with {} {} (order {})",
                    userId, order.amount(), order.currencyCode(), order.token());
            return new FundAccountResponse(order.token(), order.amount(), order.currencyCode(), order.state());
        } catch (RestClientException ex) {
            log.error("Failed to fund user {}: {}", userId, ex.getMessage());
            throw new MarqetaIntegrationException("Account could not be funded", ex);
        }
    }

    public BalanceResponse getBalance(UUID userId) {
        User user = getActiveUser(userId);

        try {
            MarqetaBalanceResponse balance = marqetaClient.getBalance(user.getMarqetaUserToken());
            MarqetaBalanceResponse.Gpa gpa = balance.gpa();
            return new BalanceResponse(gpa.availableBalance(), gpa.ledgerBalance(), gpa.currencyCode());
        } catch (RestClientException ex) {
            log.error("Failed to fetch balance for user {}: {}", userId, ex.getMessage());
            throw new MarqetaIntegrationException("Balance could not be retrieved", ex);
        }
    }

    private User getActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getUserStatus() != UserStatus.ACTIVE || user.getMarqetaUserToken() == null) {
            throw new UserNotActiveException(userId);
        }
        return user;
    }
}