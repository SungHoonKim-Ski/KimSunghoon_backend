package barley.wire.wirebarley.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;

@Schema(description = "외부 환율 API 응답")
public record ExchangeRateApiResponse(
    @Schema(description = "기준 통화")
    String base,

    @Schema(description = "환율 데이터")
    Map<String, BigDecimal> rates
) {
}
