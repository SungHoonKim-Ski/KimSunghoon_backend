package barley.wire.wirebarley.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "글로벌 송금 요청 (v2 - 자동 환전)")
public record GlobalTransferRequest(
    @Schema(description = "출금 계좌 ID", example = "1")
    @NotNull
    Long fromAccountId,

    @Schema(description = "입금 계좌 ID", example = "2")
    @NotNull
    Long toAccountId,

    @Schema(description = "이체 금액", example = "100.00")
    @NotNull @DecimalMin(value = "0.0", inclusive = false)
    BigDecimal amount
) {
}
