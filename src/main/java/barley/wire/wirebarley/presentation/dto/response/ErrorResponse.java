package barley.wire.wirebarley.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "에러 응답")
public record ErrorResponse(
    @Schema(description = "에러 코드")
    String code,

    @Schema(description = "에러 메시지")
    String message,

    @Schema(description = "발생 일시")
    LocalDateTime timestamp
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, LocalDateTime.now());
    }
}
