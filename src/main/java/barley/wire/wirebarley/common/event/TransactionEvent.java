package barley.wire.wirebarley.common.event;

import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.domain.transaction.TransactionType;
import barley.wire.wirebarley.common.util.TimeUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record TransactionEvent(
        Long accountId,
        TransactionType type,
        BigDecimal amount,
        BigDecimal fee,
        Currency currency,
        BigDecimal balanceSnapshot,
        Long relatedAccountId,
        LocalDateTime timestamp) {

    public TransactionEvent(Long accountId, TransactionType type, BigDecimal amount, BigDecimal fee, Currency currency,
            BigDecimal balanceSnapshot, Long relatedAccountId) {

        this(accountId, type, amount, fee, currency, balanceSnapshot, relatedAccountId, TimeUtil.nowDateTime());
    }

}
