package RiskShield.service;

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
    private final TransactionProcessor transactionProcessor; // NEW

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

        // CHANGED: save, then process transaction events right away
        try {
            WebhookEvent saved = webhookEventRepository.save(webhookEvent);
            log.info("Stored {} event {} (type: {})", category, token, saved.getEventType());

            if (TransactionProcessor.TRANSACTIONS_CATEGORY.equals(category)) {
                try {
                    transactionProcessor.process(saved);
                } catch (Exception ex) {
                    log.error("Stored event {} but failed to process it: {}", token, ex.getMessage());
                }
            }
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate {} event {} ignored (concurrent delivery)", category, token);
        }
    }
}