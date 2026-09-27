package RiskShield.controller;

import RiskShield.dto.CardResponse;
import RiskShield.service.CardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/{userId}/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @PostMapping
    public ResponseEntity<CardResponse> issueCard(@PathVariable UUID userId) {
        CardResponse card = cardService.issueCard(userId);
        return ResponseEntity
                .created(URI.create("/api/users/" + userId + "/cards/" + card.id()))
                .body(card);
    }

    @GetMapping
    public ResponseEntity<List<CardResponse>> getCards(@PathVariable UUID userId) {
        return ResponseEntity.ok(cardService.getCards(userId));
    }
}