package RiskShield.repository;

import RiskShield.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByMarqetaToken(String marqetaToken);
    List<Transaction> findAllByUserIdOrderByTransactionTimeDesc(UUID userId);
    boolean existsByPrecedingTransactionTokenAndType(String precedingTransactionToken, String type);
}