package RiskShield.controller;

import RiskShield.dto.BalanceResponse;
import RiskShield.dto.FundAccountRequest;
import RiskShield.dto.FundAccountResponse;
import RiskShield.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/{userId}")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/funds")
    public ResponseEntity<FundAccountResponse> fundAccount(
            @PathVariable UUID userId,
            @Valid @RequestBody FundAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.fundAccount(userId, request));
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID userId) {
        return ResponseEntity.ok(accountService.getBalance(userId));
    }
}