package barley.wire.wirebarley.domain.account;

import barley.wire.wirebarley.common.util.TimeUtil;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String accountNumber;

    @Column(nullable = false, length = 100)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Getter
    private Currency currency;

    @Column(nullable = false)
    @Getter
    private BigDecimal balance;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = TimeUtil.nowDateTime();
    }

    // v1 호환성: currency 기본값 KRW
    public Account(String accountNumber, String ownerName) {
        this(accountNumber, ownerName, Currency.KRW);
    }

    // v2: currency 명시
    public Account(String accountNumber, String ownerName, Currency currency) {
        this.accountNumber = accountNumber;
        this.ownerName = ownerName;
        this.currency = currency;
        this.balance = BigDecimal.ZERO;
    }

    public void deposit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalStateException("잔액이 부족합니다");
        }
        this.balance = this.balance.subtract(amount);
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void delete() {
        this.accountNumber = "deleted-" + this.id + "-" + this.accountNumber;
        this.deletedAt = TimeUtil.nowDateTime();
    }
}
