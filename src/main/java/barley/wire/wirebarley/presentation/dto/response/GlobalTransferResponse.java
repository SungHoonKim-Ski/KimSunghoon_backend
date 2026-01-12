package barley.wire.wirebarley.presentation.dto.response;

import barley.wire.wirebarley.domain.account.Account;
import barley.wire.wirebarley.domain.account.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "해외 이체 결과 응답")
public record GlobalTransferResponse(
    @Schema(description = "출금 계좌 ID")
    Long fromAccountId,

    @Schema(description = "수취 계좌 ID")
    Long toAccountId,

    @Schema(description = "원래 금액")
    BigDecimal originalAmount,

    @Schema(description = "출금 통화")
    Currency fromCurrency,

    @Schema(description = "변환된 금액")
    BigDecimal convertedAmount,

    @Schema(description = "수취 통화")
    Currency toCurrency,

    @Schema(description = "적용 환율")
    BigDecimal exchangeRate,

    @Schema(description = "수수료")
    BigDecimal fee,

    @Schema(description = "출금 후 잔액")
    BigDecimal fromBalance,

    @Schema(description = "수취 후 잔액")
    BigDecimal toBalance
) {
    public static GlobalTransferResponse of(
        Account from,
        Account to,
        BigDecimal originalAmount,
        Currency fromCurrency,
        BigDecimal convertedAmount,
        Currency toCurrency,
        BigDecimal exchangeRate,
        BigDecimal fee
    ) {
        return new GlobalTransferResponse(
            from.getId(),
            to.getId(),
            originalAmount,
            fromCurrency,
            convertedAmount,
            toCurrency,
            exchangeRate,
            fee,
            from.getBalance(),
            to.getBalance()
        );
    }
}
