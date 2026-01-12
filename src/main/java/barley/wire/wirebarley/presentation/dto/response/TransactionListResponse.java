package barley.wire.wirebarley.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "거래 내역 목록 응답")
public record TransactionListResponse(
    @Schema(description = "거래 내역 목록")
    List<TransactionResponse> items,

    @Schema(description = "현재 페이지 번호")
    int page,

    @Schema(description = "페이지 크기")
    int size,

    @Schema(description = "전체 요소 수")
    long totalElements,

    @Schema(description = "전체 페이지 수")
    int totalPages
) {
    public static TransactionListResponse from(Page<TransactionResponse> dtoPage) {
        return new TransactionListResponse(dtoPage.getContent(), dtoPage.getNumber(), dtoPage.getSize(),
            dtoPage.getTotalElements(), dtoPage.getTotalPages());
    }
}
