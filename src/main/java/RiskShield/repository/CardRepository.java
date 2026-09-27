package RiskShield.repository;

import RiskShield.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {
    List<Card> findAllByUserId(UUID userId);
    Optional<Card> findByMarqetaCardToken(String marqetaCardToken);
}