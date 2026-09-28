package RiskShield.service;

import RiskShield.entity.Card;
import RiskShield.entity.Transaction;
import RiskShield.entity.User;
import RiskShield.entity.WebhookEvent;
import RiskShield.marqeta.MarqetaTransactionEvent;
import RiskShield.repository.CardRepository;
import RiskShield.repository.TransactionRepository;
import RiskShield.repository.UserRepository;
import RiskShield.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessor {

    public static final String TRANSACTIONS_CATEGORY = "transactions";

    private final ObjectMapper objectMapper;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CardRepository cardRepository;
    private final WebhookEventRepository webhookEventRepository;
    private static final String CLEARING_TYPE = "authorization.clearing";
    private static final String COMPLETED_STATE = "COMPLETION";

    @Transactional
    public boolean process(WebhookEvent webhookEvent) {
        MarqetaTransactionEvent event =
                objectMapper.readValue(webhookEvent.getPayload(), MarqetaTransactionEvent.class);

        Optional<User> user = userRepository.findByMarqetaUserToken(event.userToken());
        if (user.isEmpty()) {
            log.warn("Transaction {} references unknown user {}; leaving unprocessed",
                    event.token(), event.userToken());
            return false;
        }

        Card card = event.cardToken() == null ? null
                : cardRepository.findByMarqetaCardToken(event.cardToken()).orElse(null);

        Transaction transaction = transactionRepository.findByMarqetaToken(event.token())
                .orElseGet(Transaction::new);

        transaction.setMarqetaToken(event.token());
        transaction.setUser(user.get());
        transaction.setCard(card);
        transaction.setType(event.type());
        transaction.setState(event.state());
        transaction.setAmount(event.amount());
        transaction.setCurrencyCode(event.currencyCode());
        transaction.setTransactionTime(parseTime(event));
        transaction.setPrecedingTransactionToken(event.precedingRelatedTransactionToken());

        if (event.response() != null) {
            transaction.setResponseCode(event.response().code());
            transaction.setResponseMemo(event.response().memo());
        }
        if (event.cardAcceptor() != null) {
            transaction.setMerchantName(event.cardAcceptor().name());
            transaction.setMerchantMid(event.cardAcceptor().mid());
            transaction.setMerchantMcc(event.cardAcceptor().mcc());
            transaction.setMerchantCity(event.cardAcceptor().city());
            transaction.setMerchantCountry(event.cardAcceptor().countryCode());
        }
        if (event.gpa() != null) {
            transaction.setAvailableBalanceAfter(event.gpa().availableBalance());
        }
        if (event.fraud() != null && event.fraud().network() != null) {
            transaction.setNetworkRiskScore(event.fraud().network().transactionRiskScore());
        }

        if ("authorization".equals(event.type())
                && transactionRepository.existsByPrecedingTransactionTokenAndType(event.token(), CLEARING_TYPE)) {
            transaction.setState(COMPLETED_STATE);
        }

        transactionRepository.save(transaction);

// When a clearing arrives, mark the authorization it completes as settled.
        if (CLEARING_TYPE.equals(event.type()) && event.precedingRelatedTransactionToken() != null) {
            transactionRepository.findByMarqetaToken(event.precedingRelatedTransactionToken())
                    .ifPresent(authorization -> {
                        authorization.setState(COMPLETED_STATE);
                        transactionRepository.save(authorization);
                        log.info("Authorization {} completed by clearing {}",
                                authorization.getMarqetaToken(), event.token());
                    });
        }
        webhookEvent.setProcessed(true);
        webhookEventRepository.save(webhookEvent);

        log.info("Processed transaction {} ({} / {}) for user {}",
                event.token(), event.type(), event.state(), user.get().getId());
        return true;
    }

    public int processPending() {
        List<WebhookEvent> pending = webhookEventRepository
                .findAllByEventCategoryAndProcessedFalseOrderByReceivedAtAsc(TRANSACTIONS_CATEGORY);

        int processed = 0;
        for (WebhookEvent event : pending) {
            try {
                if (process(event)) {
                    processed++;
                }
            } catch (Exception ex) {
                log.error("Failed to process webhook event {}: {}", event.getEventToken(), ex.getMessage());
            }
        }
        return processed;
    }

    private Instant parseTime(MarqetaTransactionEvent event) {
        String time = event.userTransactionTime() != null ? event.userTransactionTime() : event.createdTime();
        return time != null ? Instant.parse(time) : Instant.now();
    }
}