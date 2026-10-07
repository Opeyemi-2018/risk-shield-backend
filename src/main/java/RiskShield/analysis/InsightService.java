package RiskShield.analysis;

import RiskShield.dto.TransactionInsightResponse;
import RiskShield.entity.Transaction;
import RiskShield.entity.TransactionInsight;
import RiskShield.exception.TransactionNotFoundException;
import RiskShield.exception.UserNotFoundException;
import RiskShield.repository.TransactionInsightRepository;
import RiskShield.repository.TransactionRepository;
import RiskShield.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private static final int LOOKBACK_DAYS = SpendingProfileService.DEFAULT_LOOKBACK_DAYS;
    private static final int RECALCULATE_DAYS = 180;
    private static final Set<String> APPROVED_STATES = Set.of("PENDING", "COMPLETION", "CLEARED");

    private final TransactionRepository transactionRepository;
    private final TransactionInsightRepository insightRepository;
    private final UserRepository userRepository;
    private final SpendingProfileService spendingProfileService;
    private final AnomalyDetector anomalyDetector;
    private final ObjectMapper objectMapper;

    /** Evaluates one purchase. Only approved card purchases (authorizations) are evaluated. */
    @Transactional
    public Optional<TransactionInsight> evaluate(Transaction tx) {
        if (!"authorization".equals(tx.getType()) || !APPROVED_STATES.contains(tx.getState())) {
            return Optional.empty();
        }

        UUID userId = tx.getUser().getId();
        Instant time = tx.getTransactionTime();

        // The profile only includes purchases BEFORE this one.
        SpendingProfile profile = spendingProfileService.buildProfile(
                userId, time.minus(LOOKBACK_DAYS, ChronoUnit.DAYS), time, LOOKBACK_DAYS);
        int previousHour = transactionRepository
                .findPurchases(userId, time.minus(1, ChronoUnit.HOURS), time)
                .size();

        AnomalyDetector.Assessment assessment = anomalyDetector.assess(tx, profile, previousHour);

        TransactionInsight insight = insightRepository.findByTransactionId(tx.getId())
                .orElseGet(TransactionInsight::new);
        insight.setTransaction(tx);
        insight.setUser(tx.getUser());
        insight.setScore(assessment.score());
        insight.setLevel(assessment.level());
        insight.setSignals(objectMapper.writeValueAsString(assessment.signals()));
        insight.setEvaluatedAt(Instant.now());

        TransactionInsight saved = insightRepository.save(insight);
        if (assessment.level() == InsightLevel.UNUSUAL || assessment.level() == InsightLevel.HIGH) {
            log.info("Transaction {} flagged {} (score {})", tx.getId(), assessment.level(), assessment.score());
        }
        return Optional.of(saved);
    }

    /** Used by the webhook flow: the webhook event token is the Marqeta transaction token. */
    @Transactional
    public void evaluateByMarqetaToken(String marqetaToken) {
        transactionRepository.findByMarqetaToken(marqetaToken).ifPresent(this::evaluate);
    }

    /** Re-evaluates every purchase in the last 180 days, oldest first. */
    @Transactional
    public int recalculateForUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        Instant now = Instant.now();
        List<Transaction> purchases = transactionRepository
                .findPurchases(userId, now.minus(RECALCULATE_DAYS, ChronoUnit.DAYS), now);

        int evaluated = 0;
        for (Transaction tx : purchases) {
            if (evaluate(tx).isPresent()) {
                evaluated++;
            }
        }
        log.info("Recalculated {} insights for user {}", evaluated, userId);
        return evaluated;
    }

    /** Insights at or above the given level, newest first. */
    @Transactional(readOnly = true)
    public List<TransactionInsightResponse> getInsights(UUID userId, InsightLevel minimumLevel) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        List<InsightLevel> levels = Arrays.stream(InsightLevel.values())
                .filter(level -> level.ordinal() >= minimumLevel.ordinal())
                .toList();

        return insightRepository.findForUser(userId, levels).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionInsightResponse getInsight(UUID userId, UUID transactionId) {
        TransactionInsight insight = insightRepository.findByTransactionId(transactionId)
                .filter(i -> i.getUser().getId().equals(userId))
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
        return toResponse(insight);
    }

    private TransactionInsightResponse toResponse(TransactionInsight insight) {
        Transaction tx = insight.getTransaction();
        List<InsightSignal> signals = parseSignals(insight);        return new TransactionInsightResponse(
                tx.getId(),
                tx.getMerchantName(),
                MerchantCategories.label(tx.getMerchantMcc()),
                tx.getAmount(),
                tx.getCurrencyCode(),
                tx.getTransactionTime(),
                tx.isSynthetic(),
                insight.getScore(),
                insight.getLevel(),
                signals,
                insight.getEvaluatedAt()
        );
    }

    /** Facts for explaining one insight. Checks the transaction belongs to this user. */
    @Transactional(readOnly = true)
    public InsightExplanationFacts buildExplanationFacts(UUID userId, UUID transactionId) {
        TransactionInsight insight = insightRepository.findByTransactionId(transactionId)
                .filter(i -> i.getUser().getId().equals(userId))
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        return InsightExplanationFacts.of(
                insight.getTransaction(),
                insight.getScore(),
                insight.getLevel(),
                parseSignals(insight));
    }

    private List<InsightSignal> parseSignals(TransactionInsight insight) {
        return List.of(objectMapper.readValue(insight.getSignals(), InsightSignal[].class));
    }
}