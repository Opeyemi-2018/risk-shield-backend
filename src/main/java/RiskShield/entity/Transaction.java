package RiskShield.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_transactions_user_time", columnList = "user_id, transaction_time"),
                @Index(name = "idx_transactions_preceding", columnList = "preceding_transaction_token")
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String marqetaToken;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private Card card;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currencyCode;

    private String responseCode;
    private String responseMemo;

    private String merchantName;
    private String merchantMid;

    @Column(length = 5)
    private String merchantMcc;

    private String merchantCity;
    private String merchantCountry;

    private Integer networkRiskScore;

    @Column(precision = 19, scale = 2)
    private BigDecimal availableBalanceAfter;

    @Column(name = "transaction_time", nullable = false)
    private Instant transactionTime;

    @Column(name = "preceding_transaction_token")
    private String precedingTransactionToken;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean synthetic;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}