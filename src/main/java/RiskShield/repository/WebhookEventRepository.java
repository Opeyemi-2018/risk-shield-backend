package RiskShield.repository;

import RiskShield.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    boolean existsByEventCategoryAndEventToken(String eventCategory, String eventToken);
    List<WebhookEvent> findAllByEventCategoryAndProcessedFalseOrderByReceivedAtAsc(String eventCategory);
    List<WebhookEvent> findAllByProcessedFalseOrderByReceivedAtAsc();
}