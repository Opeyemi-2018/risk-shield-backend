package RiskShield.controller;

import RiskShield.analysis.SpendingProfile;
import RiskShield.analysis.SpendingProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AnalysisController {

    private final SpendingProfileService spendingProfileService;

    @GetMapping("/api/users/{userId}/spending-profile")
    public ResponseEntity<SpendingProfile> getSpendingProfile(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "90") int days) {
        int lookback = Math.max(7, Math.min(days, 365));
        return ResponseEntity.ok(spendingProfileService.buildProfile(userId, lookback));
    }
}