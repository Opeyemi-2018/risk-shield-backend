package RiskShield.service;

import RiskShield.dto.ExplanationResponse;
import RiskShield.explanation.AiExplanationService;
import RiskShield.explanation.ExplanationFactBuilder;
import RiskShield.explanation.ExplanationFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExplanationService {

    private final ExplanationFactBuilder factBuilder;
    private final AiExplanationService aiExplanationService;

    // Deliberately not @Transactional: the database work happens inside factBuilder.build(),
    // so no database connection is held while waiting for the AI.
    public ExplanationResponse explainDecline(UUID userId, UUID transactionId) {
        ExplanationFacts facts = factBuilder.build(userId, transactionId);

        Optional<String> aiText = aiExplanationService.explain(facts);
        String explanation = aiText.orElseGet(facts::fallbackText);
        String source = aiText.isPresent() ? "AI" : "RULES";

        log.info("Explained transaction {} ({}) using {}", transactionId, facts.category(), source);

        return new ExplanationResponse(
                facts.transactionId(),
                facts.category(),
                facts.headline(),
                explanation,
                facts.nextStep(),
                source,
                Instant.now()
        );
    }
}