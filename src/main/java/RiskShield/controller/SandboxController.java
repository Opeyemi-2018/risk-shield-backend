package RiskShield.controller;

import RiskShield.dto.SeedHistoryRequest;
import RiskShield.dto.SeedHistoryResponse;
import RiskShield.dto.SimulatePurchaseRequest;
import RiskShield.dto.SimulationResultResponse;
import RiskShield.service.SandboxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/sandbox")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "riskshield.sandbox.enabled", havingValue = "true")
public class SandboxController {

    private final SandboxService sandboxService;

    @PostMapping("/users/{userId}/purchases")
    public ResponseEntity<SimulationResultResponse> simulatePurchase(
            @PathVariable UUID userId,
            @Valid @RequestBody SimulatePurchaseRequest request) {
        return ResponseEntity.ok(sandboxService.simulatePurchase(userId, request));
    }

    @PostMapping("/transactions/{transactionId}/clear")
    public ResponseEntity<SimulationResultResponse> simulateClearing(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(sandboxService.simulateClearing(transactionId));
    }

    @PostMapping("/users/{userId}/seed-history")
    public ResponseEntity<SeedHistoryResponse> seedHistory(
            @PathVariable UUID userId,
            @Valid @RequestBody(required = false) SeedHistoryRequest request) {
        SeedHistoryRequest effective = request != null ? request : new SeedHistoryRequest(null, null, null);
        return ResponseEntity.ok(sandboxService.seedHistory(userId, effective));
    }
}