package RiskShield.dto;

import RiskShield.explanation.DeclineCategory;

import java.time.Instant;
import java.util.UUID;

public record ExplanationResponse(
        UUID transactionId,
        DeclineCategory category,
        String headline,
        String explanation,
        String nextStep,
        String source,          // "AI" or "RULES"
        Instant generatedAt
) {
}