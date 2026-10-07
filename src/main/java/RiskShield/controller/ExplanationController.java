package RiskShield.controller;

import RiskShield.dto.ExplanationResponse;
import RiskShield.service.ExplanationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ExplanationController {

    private final ExplanationService explanationService;

    @GetMapping("/api/users/{userId}/transactions/{transactionId}/explanation")
    public ResponseEntity<ExplanationResponse> explain(@PathVariable UUID userId,
                                                       @PathVariable UUID transactionId) {
        return ResponseEntity.ok(explanationService.explainDecline(userId, transactionId));
    }
}