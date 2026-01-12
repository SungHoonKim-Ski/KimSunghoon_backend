package barley.wire.wirebarley.presentation.dto.response;

import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.domain.transaction.TransactionType;
import barley.wire.wirebarley.domain.transaction.Transaction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "단일 거래 내역 응답")
public record TransactionResponse(
    @Schema(description = "거래 ID")
    Long id,

    @Schema(description = "거래 유형 (DEPOSIT, WITHDRAW, TRANSFER_IN, TRANSFER_OUT)")
    TransactionType type,

    @Schema(description = "거래 금액")
    BigDecimal amount,

    @Schema(description = "거래 통화")
    Currency currency,

    @Schema(description = "수수료")
    BigDecimal fee,

    @Schema(description = "거래 후 잔액 스냅샷")
    BigDecimal balanceSnapshot,

    @Schema(description = "관련 계좌 ID (이체 시)")
    Long relatedAccountId,

    @Schema(description = "거래 일시")
    LocalDateTime createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
            transaction.getId(),
            transaction.getType(),
            transaction.getAmount(),
            transaction.getCurrency(),
            transaction.getFee(),
            transaction.getBalanceSnapshot(),
            transaction.getRelatedAccountId(),
            transaction.getCreatedAt());
    }
}
