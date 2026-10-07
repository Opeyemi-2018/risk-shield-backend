package RiskShield.repository;

import RiskShield.entity.Card;
import RiskShield.entity.CardStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {
    List<Card> findAllByUserId(UUID userId);
    Optional<Card> findByMarqetaCardToken(String marqetaCardToken);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Card> findWithLockByMarqetaCardToken(String marqetaCardToken);
    Optional<Card> findFirstByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, CardStatus status);
}