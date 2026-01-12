package barley.wire.wirebarley.presentation.dto.response;

import barley.wire.wirebarley.domain.account.Account;
import barley.wire.wirebarley.domain.account.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "계좌 정보 응답")
public record AccountResponse(
    @Schema(description = "계좌 ID", example = "1")
    Long id,

    @Schema(description = "계좌 번호", example = "123-456-789")
    String accountNumber,

    @Schema(description = "소유주 성명", example = "홍길동")
    String ownerName,

    @Schema(description = "현재 잔액", example = "50000.00")
    BigDecimal balance,

    @Schema(description = "통화", example = "KRW") Currency currency) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getOwnerName(),
                account.getBalance(),
                account.getCurrency());
    }
}
