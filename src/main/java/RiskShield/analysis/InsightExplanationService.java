package RiskShield.analysis;

import RiskShield.dto.InsightExplanationResponse;
import RiskShield.explanation.AiExplanationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightExplanationService {

    private final InsightService insightService;
    private final AiExplanationService aiExplanationService;

    // Not @Transactional: the database work finishes inside buildExplanationFacts,
    // so no connection is held while waiting for the AI.
    public InsightExplanationResponse explain(UUID userId, UUID transactionId) {
        InsightExplanationFacts facts = insightService.buildExplanationFacts(userId, transactionId);

        Optional<String> aiText = facts.needsExplanation()
                ? aiExplanationService.explainInsight(facts)
                : Optional.empty();

        String explanation = aiText.orElseGet(facts::fallbackText);
        String source = aiText.isPresent() ? "AI" : "RULES";

        log.info("Explained insight for transaction {} ({}) using {}", transactionId, facts.level(), source);

        return new InsightExplanationResponse(
                facts.transactionId(),
                facts.level(),
                facts.score(),
                facts.headline(),
                explanation,
                facts.nextStep(),
                facts.reasons(),
                source,
                Instant.now()
        );
    }
}