package barley.wire.wirebarley.domain.transaction;

import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.common.util.TimeUtil;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transactions", indexes = { @Index(name = "idx_account_created", columnList = "accountId,createdAt") })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Getter
    @Column(nullable = false)
    private BigDecimal amount;

    @Getter
    @Column(nullable = false)
    private BigDecimal fee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Getter
    private Currency currency;

    @Getter
    @Column(name = "balance_snapshot")
    private BigDecimal balanceSnapshot;

    @Getter
    @Column(name = "related_account_id")
    private Long relatedAccountId;

    @Getter
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = TimeUtil.nowDateTime();
    }

    public Transaction(Long accountId, TransactionType type, BigDecimal amount, BigDecimal fee,
            BigDecimal balanceSnapshot, Long relatedAccountId, Currency currency) {
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.fee = fee;
        this.balanceSnapshot = balanceSnapshot;
        this.relatedAccountId = relatedAccountId;
        this.currency = currency;
    }
}
