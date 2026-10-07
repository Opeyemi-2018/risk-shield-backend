package RiskShield.repository;

import RiskShield.entity.Transaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query("""
        select t from Transaction t
        where t.user.id = :userId
          and t.type = 'authorization'
          and t.state in ('PENDING', 'COMPLETION', 'CLEARED')
          and t.transactionTime >= :from
          and t.transactionTime < :to
        order by t.transactionTime asc
        """)
    List<Transaction> findPurchases(@Param("userId") UUID userId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);
    @Query("""
        select t from Transaction t
        where t.user.id = :userId
          and t.state = 'DECLINED'
          and t.transactionTime >= :from
          and t.transactionTime < :to
        order by t.transactionTime desc
        """)
    List<Transaction> findDeclined(@Param("userId") UUID userId,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);
    Optional<Transaction> findByMarqetaToken(String marqetaToken);
    @EntityGraph(attributePaths = "card")
    List<Transaction> findAllByUserIdOrderByTransactionTimeDesc(UUID userId);    long deleteByUserIdAndSyntheticTrue(UUID userId);
    boolean existsByPrecedingTransactionTokenAndType(String precedingTransactionToken, String type);

}