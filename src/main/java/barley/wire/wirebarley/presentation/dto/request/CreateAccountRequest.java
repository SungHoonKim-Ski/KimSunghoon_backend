package barley.wire.wirebarley.presentation.dto.request;

import barley.wire.wirebarley.domain.account.Account;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "계좌 생성 요청")
public record CreateAccountRequest(
    @Schema(description = "계좌 번호", example = "123-456-789")
    @NotBlank
    String accountNumber,

    @Schema(description = "소유주 성명", example = "홍길동")
    @NotBlank
    String ownerName
) {
    public static Account toEntity(CreateAccountRequest request) {
        return new Account(request.accountNumber(), request.ownerName());
    }
}
