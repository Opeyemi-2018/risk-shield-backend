package RiskShield.repository;

import RiskShield.analysis.InsightLevel;
import RiskShield.entity.Transaction;
import RiskShield.entity.TransactionInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionInsightRepository extends JpaRepository<TransactionInsight, UUID> {

    Optional<TransactionInsight> findByTransactionId(UUID transactionId);

    @Query("""
            select i from TransactionInsight i
            join fetch i.transaction t
            where i.user.id = :userId
              and i.level in :levels
            order by t.transactionTime desc
            """)
    List<TransactionInsight> findForUser(@Param("userId") UUID userId,
                                         @Param("levels") Collection<InsightLevel> levels);


}