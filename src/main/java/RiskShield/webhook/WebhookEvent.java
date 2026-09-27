package RiskShield.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "webhook_events",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_category", "event_token"})
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_category", nullable = false)
    private String eventCategory;

    @Column(name = "event_token", nullable = false)
    private String eventToken;

    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false)
    private boolean processed;

    @Column(nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    public void onCreate() {
        this.receivedAt = LocalDateTime.now();
    }
}