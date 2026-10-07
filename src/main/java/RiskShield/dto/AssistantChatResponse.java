package RiskShield.dto;

import java.time.Instant;
import java.util.List;

public record AssistantChatResponse(
        String reply,
        List<String> toolsUsed,   // e.g. ["spending summary"], for showing what was checked
        boolean fallback,         // true if the AI failed and a standard message was returned
        Instant generatedAt
) {
}