package RiskShield.controller;

import RiskShield.assistant.AssistantService;
import RiskShield.dto.AssistantChatRequest;
import RiskShield.dto.AssistantChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @PostMapping("/api/users/{userId}/assistant/chat")
    public ResponseEntity<AssistantChatResponse> chat(@PathVariable UUID userId,
                                                      @Valid @RequestBody AssistantChatRequest request) {
        return ResponseEntity.ok(assistantService.chat(userId, request));
    }
}