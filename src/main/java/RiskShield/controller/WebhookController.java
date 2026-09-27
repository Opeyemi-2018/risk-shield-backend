package RiskShield.controller;

import RiskShield.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/marqeta")
    public ResponseEntity<Map<String, String>> receiveMarqetaWebhook(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody String rawBody) {

        if (!webhookService.isAuthorized(authorization)) {
            log.warn("Rejected webhook call with invalid credentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        webhookService.handleNotification(rawBody);
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}