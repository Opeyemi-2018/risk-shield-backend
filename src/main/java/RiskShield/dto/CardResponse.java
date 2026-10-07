package RiskShield.dto;

import RiskShield.entity.Card;
import RiskShield.entity.CardStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record CardResponse(
        UUID id,
        String lastFour,
        String expiration,
        CardStatus status,
        String statusReason,
        LocalDateTime createdAt
) {
    public static CardResponse from(Card card) {
        return new CardResponse(
                card.getId(),
                card.getLastFour(),
                card.getExpiration(),
                card.getStatus(),
                card.getStatusReason(),
                card.getCreatedAt()
        );
    }
}