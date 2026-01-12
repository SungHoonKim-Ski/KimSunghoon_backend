package barley.wire.wirebarley.presentation.dto.response;

import barley.wire.wirebarley.domain.account.Account;
import barley.wire.wirebarley.domain.account.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "잔액 정보 응답")
public record BalanceResponse(
    @Schema(description = "계좌 ID", example = "1")
    Long accountId,

    @Schema(description = "현재 잔액", example = "10000.00")
    BigDecimal balance,

    @Schema(description = "통화", example = "KRW")
    Currency currency
) {
    public static BalanceResponse from(Account account) {
        return new BalanceResponse(
                account.getId(),
                account.getBalance(),
                account.getCurrency());
    }
}
