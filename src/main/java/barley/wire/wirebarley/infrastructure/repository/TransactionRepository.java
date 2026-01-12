package barley.wire.wirebarley.infrastructure.repository;

import barley.wire.wirebarley.domain.transaction.Transaction;
import barley.wire.wirebarley.domain.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
        + "WHERE t.accountId = :accountId AND t.type = :type AND t.createdAt >= :startOfDay")
    BigDecimal sumAmountByAccountIdAndTypeAndCreatedAtAfter(@Param("accountId") Long accountId,
        @Param("type") TransactionType type, @Param("startOfDay") LocalDateTime startOfDay);
}
