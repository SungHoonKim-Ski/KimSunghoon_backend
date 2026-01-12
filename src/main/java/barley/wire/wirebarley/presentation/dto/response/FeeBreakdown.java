package barley.wire.wirebarley.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "수수료 상세 정보")
public record FeeBreakdown(
    @Schema(description = "송금 수수료")
    BigDecimal transferFee,

    @Schema(description = "환전 수수료")
    BigDecimal exchangeFee,

    @Schema(description = "총 수수료 합계")
    BigDecimal totalFee
) {
}
