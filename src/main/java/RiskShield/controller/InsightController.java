package RiskShield.controller;

import RiskShield.analysis.InsightExplanationService;
import RiskShield.analysis.InsightLevel;
import RiskShield.analysis.InsightService;
import RiskShield.dto.InsightExplanationResponse;
import RiskShield.dto.TransactionInsightResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    @GetMapping("/api/users/{userId}/insights")
    public ResponseEntity<List<TransactionInsightResponse>> getInsights(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "UNUSUAL") InsightLevel minLevel) {
        return ResponseEntity.ok(insightService.getInsights(userId, minLevel));
    }

    @GetMapping("/api/users/{userId}/transactions/{transactionId}/insight")
    public ResponseEntity<TransactionInsightResponse> getInsight(@PathVariable UUID userId,
                                                                 @PathVariable UUID transactionId) {
        return ResponseEntity.ok(insightService.getInsight(userId, transactionId));
    }

    @PostMapping("/api/users/{userId}/insights/recalculate")
    public ResponseEntity<Map<String, Integer>> recalculate(@PathVariable UUID userId) {
        return ResponseEntity.ok(Map.of("evaluated", insightService.recalculateForUser(userId)));
    }
    private final InsightExplanationService insightExplanationService;

    @GetMapping("/api/users/{userId}/transactions/{transactionId}/insight/explanation")
    public ResponseEntity<InsightExplanationResponse> explainInsight(@PathVariable UUID userId,
                                                                     @PathVariable UUID transactionId) {
        return ResponseEntity.ok(insightExplanationService.explain(userId, transactionId));
    } 
}