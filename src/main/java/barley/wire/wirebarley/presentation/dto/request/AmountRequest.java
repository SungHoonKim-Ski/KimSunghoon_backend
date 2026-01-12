package barley.wire.wirebarley.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "금액 요청 (입금/출금)")
public record AmountRequest(
    @Schema(description = "금액", example = "10000.00")
    @NotNull @DecimalMin(value = "0.0", inclusive = false)
    BigDecimal amount
) {
}
