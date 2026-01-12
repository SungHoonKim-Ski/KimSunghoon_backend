package barley.wire.wirebarley.presentation.dto.request;

import barley.wire.wirebarley.domain.account.Account;
import barley.wire.wirebarley.domain.account.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "계좌 생성 요청 (v2 - 통화 지원)")
public record CreateGlobalAccountRequest(
    @Schema(description = "계좌 번호", example = "123-456-789")
    @NotBlank
    String accountNumber,

    @Schema(description = "소유주 성명", example = "홍길동")
    @NotBlank
    String ownerName,

    @Schema(description = "통화", example = "USD")
    @NotNull
    Currency currency
) {
    public static Account toEntity(CreateGlobalAccountRequest request) {
        return new Account(request.accountNumber(), request.ownerName(), request.currency());
    }
}
