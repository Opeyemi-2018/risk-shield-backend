package RiskShield.dto;

import RiskShield.analysis.InsightLevel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InsightExplanationResponse(
        UUID transactionId,
        InsightLevel level,
        int score,
        String headline,
        String explanation,
        String nextStep,
        List<String> reasons,     // the factual reasons, for showing as a list too
        String source,            // "AI" or "RULES"
        Instant generatedAt
) {
}