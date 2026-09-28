package RiskShield.service;

import RiskShield.entity.Card;
import RiskShield.entity.CardStatus;
import RiskShield.entity.WebhookEvent;
import RiskShield.marqeta.MarqetaCardTransitionEvent;
import RiskShield.repository.CardRepository;
import RiskShield.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;                       // NEW
import java.time.format.DateTimeFormatter;             // NEW
import java.time.format.DateTimeParseException;        // NEW
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardEventProcessor {

    public static final String CARDS_CATEGORY = "cards";

    // NEW: Marqeta's millisecond timestamp format, e.g. 2026-09-28T23:06:24:984Z
    private static final DateTimeFormatter MARQETA_MILLIS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss:SSSX");

    private final ObjectMapper objectMapper;
    private final CardRepository cardRepository;
    private final WebhookEventRepository webhookEventRepository;

    @Transactional
    public boolean process(WebhookEvent webhookEvent) {
        MarqetaCardTransitionEvent event =
                objectMapper.readValue(webhookEvent.getPayload(), MarqetaCardTransitionEvent.class);

        // CHANGED: was findByMarqetaCardToken — now locks the card row
        Optional<Card> found = cardRepository.findWithLockByMarqetaCardToken(event.cardToken());
        if (found.isEmpty()) {
            log.warn("Card event {} references unknown card {}; leaving unprocessed",
                    event.token(), event.cardToken());
            return false;
        }

        Card card = found.get();

        // CHANGED: was the Instant.parse(...) line — now uses millisecond precision
        Instant eventTime = parseEventTime(event);

        if (card.getStatusChangedAt() != null && eventTime.isBefore(card.getStatusChangedAt())) {
            log.info("Ignoring stale card event {} for card {} (older than current status)",
                    event.token(), card.getId());
        } else {
            CardStatus newStatus = CardStatus.fromMarqeta(event.state());
            log.info("Card {} status {} -> {} ({})",
                    card.getId(), card.getStatus(), newStatus, event.reason());
            card.setStatus(newStatus);
            card.setStatusReason(event.reason());
            card.setStatusChangedAt(eventTime);
            cardRepository.save(card);
        }

        webhookEvent.setProcessed(true);
        webhookEventRepository.save(webhookEvent);
        return true;
    }

    // NEW
    private Instant parseEventTime(MarqetaCardTransitionEvent event) {
        if (event.createdTimestamp() != null) {
            try {
                return OffsetDateTime.parse(event.createdTimestamp(), MARQETA_MILLIS_FORMAT).toInstant();
            } catch (DateTimeParseException ex) {
                log.warn("Could not parse created_timestamp '{}', falling back to created_time",
                        event.createdTimestamp());
            }
        }
        return event.createdTime() != null ? Instant.parse(event.createdTime()) : Instant.now();
    }
}