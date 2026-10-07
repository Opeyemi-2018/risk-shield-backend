package RiskShield.service;

import RiskShield.analysis.InsightService;                    // NEW
import RiskShield.entity.WebhookEvent;
import RiskShield.repository.WebhookEventRepository;
import RiskShield.webhook.WebhookProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookProperties webhookProperties;
    private final ObjectMapper objectMapper;
    private final TransactionProcessor transactionProcessor;
    private final CardEventProcessor cardEventProcessor;
    private final InsightService insightService;              // NEW

    public boolean isAuthorized(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Basic ")) {
            return false;
        }
        String expected = webhookProperties.username() + ":" + webhookProperties.password();
        String provided;
        try {
            provided = new String(
                    Base64.getDecoder().decode(authorizationHeader.substring(6)),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    public void handleNotification(String rawBody) {
        Map<String, Object> notification = objectMapper.readValue(rawBody, Map.class);

        for (Map.Entry<String, Object> entry : notification.entrySet()) {
            String category = entry.getKey();

            if ("pings".equals(category)) {
                log.info("Received Marqeta webhook ping");
                continue;
            }
            if (!(entry.getValue() instanceof List<?> events)) {
                log.warn("Unexpected webhook structure for category {}", category);
                continue;
            }

            for (Object item : events) {
                if (item instanceof Map<?, ?> event) {
                    saveEvent(category, (Map<String, Object>) event);
                }
            }
        }
    }

    public int reprocessPending() {
        int processed = 0;
        for (WebhookEvent event : webhookEventRepository.findAllByProcessedFalseOrderByReceivedAtAsc()) {
            if (dispatch(event)) {
                processed++;
            }
        }
        return processed;
    }

    // CHANGED: transactions now also get an insight evaluation after processing
    private boolean dispatch(WebhookEvent event) {
        try {
            return switch (event.getEventCategory()) {
                case TransactionProcessor.TRANSACTIONS_CATEGORY -> {
                    boolean processed = transactionProcessor.process(event);
                    if (processed) {
                        evaluateInsightSafely(event.getEventToken());
                    }
                    yield processed;
                }
                case CardEventProcessor.CARDS_CATEGORY -> cardEventProcessor.process(event);
                default -> false; // stored for later; no processor for this category yet
            };
        } catch (Exception ex) {
            log.error("Failed to process {} event {}: {}",
                    event.getEventCategory(), event.getEventToken(), ex.getMessage());
            return false;
        }
    }

    // NEW
    private void evaluateInsightSafely(String marqetaToken) {
        try {
            insightService.evaluateByMarqetaToken(marqetaToken);
        } catch (Exception ex) {
            // Analysis must never break transaction processing.
            log.warn("Insight evaluation failed for transaction {}: {}", marqetaToken, ex.getMessage());
        }
    }

    private void saveEvent(String category, Map<String, Object> event) {
        Object tokenValue = event.get("token");
        if (tokenValue == null) {
            log.warn("Skipping {} event without a token", category);
            return;
        }
        String token = tokenValue.toString();

        if (webhookEventRepository.existsByEventCategoryAndEventToken(category, token)) {
            log.info("Duplicate {} event {} ignored", category, token);
            return;
        }

        Object type = event.get("type");
        WebhookEvent webhookEvent = WebhookEvent.builder()
                .eventCategory(category)
                .eventToken(token)
                .eventType(type != null ? type.toString() : null)
                .payload(objectMapper.writeValueAsString(event))
                .processed(false)
                .build();

        try {
            WebhookEvent saved = webhookEventRepository.save(webhookEvent);
            log.info("Stored {} event {} (type: {})", category, token, saved.getEventType());
            dispatch(saved);
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate {} event {} ignored (concurrent delivery)", category, token);
        }
    }
}