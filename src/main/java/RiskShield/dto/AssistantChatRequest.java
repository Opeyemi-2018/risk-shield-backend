package RiskShield.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssistantChatRequest(

        @NotBlank(message = "Message is required")
        @Size(max = 500, message = "Message must be at most 500 characters")
        String message,

        @Valid
        @Size(max = 10, message = "At most 10 previous messages")
        List<ChatTurn> history
) {
}