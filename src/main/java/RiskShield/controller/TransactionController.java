package RiskShield.controller;

import RiskShield.dto.TransactionResponse;
import RiskShield.service.TransactionProcessor;
import RiskShield.service.TransactionService;
import RiskShield.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionProcessor transactionProcessor;
    private final WebhookService webhookService;

    @GetMapping("/api/users/{userId}/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(@PathVariable UUID userId) {
        return ResponseEntity.ok(transactionService.getTransactions(userId));
    }

    // Temporary admin endpoint: will be protected once authentication is added.
    @PostMapping("/api/admin/webhook-events/reprocess")
    public ResponseEntity<Map<String, Integer>> reprocess() {
        return ResponseEntity.ok(Map.of("processed", webhookService.reprocessPending()));
    }
}