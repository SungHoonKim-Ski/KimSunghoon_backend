package barley.wire.wirebarley.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "금액 입금 - 계좌 통화에 맞게 자동 입금")
public record GlobalAmountRequest(
    @Schema(description = "금액", example = "1000.00")
    @NotNull
    BigDecimal amount
) {
}
