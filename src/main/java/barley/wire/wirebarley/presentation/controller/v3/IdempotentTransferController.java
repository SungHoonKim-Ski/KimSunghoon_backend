package barley.wire.wirebarley.presentation.controller.v3;

import barley.wire.wirebarley.application.v2.GlobalTransferAppService;
import barley.wire.wirebarley.presentation.dto.request.GlobalTransferRequest;
import barley.wire.wirebarley.presentation.dto.response.GlobalTransferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v3")
@RequiredArgsConstructor
@Tag(name = "3. 해외 송금 (멱등성 보장)", description = "추가 구현 : 실시간 환율 적용 + 중복 요청 방지")
public class IdempotentTransferController {

    private final GlobalTransferAppService globalTransferAppService;

    @Operation(summary = "해외 송금 (멱등성 보장)", description = "실시간 환율을 적용하여 글로벌 송금을 실행합니다. Idempotency-Key로 중복 송금을 방지합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "글로벌 송금 성공", content = @Content(schema = @Schema(implementation = GlobalTransferResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "잔액 부족")})
    @PostMapping("/global-transfers")
    public ResponseEntity<GlobalTransferResponse> globalTransfer(@Valid @RequestBody GlobalTransferRequest request,
             @Parameter(description = "멱등성 키 (UUID 형식)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
             @RequestHeader("Idempotency-Key") UUID idempotencyKey
    ) {

        GlobalTransferResponse response = globalTransferAppService.globalTransfer(request);
        return ResponseEntity.ok(response);
    }
}
