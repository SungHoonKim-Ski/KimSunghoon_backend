package barley.wire.wirebarley.presentation.dto.response;

import barley.wire.wirebarley.domain.account.Account;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "국내 이체 결과 응답")
public record TransferResponse(
    @Schema(description = "출금 계좌 ID")
    Long fromAccountId,

    @Schema(description = "수취 계좌 ID")
    Long toAccountId,

    @Schema(description = "이체 금액")
    BigDecimal amount,

    @Schema(description = "수수료")
    BigDecimal fee,

    @Schema(description = "출금 후 잔액")
    BigDecimal fromBalance,

    @Schema(description = "수취 후 잔액")
    BigDecimal toBalance) {

    public static TransferResponse of(
        Account from,
        Account to,
        BigDecimal amount,
        BigDecimal fee) {

        return new TransferResponse(
            from.getId(),
            to.getId(),
            amount,
            fee,
            from.getBalance(),
            to.getBalance());
    }
}
