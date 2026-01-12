package barley.wire.wirebarley.presentation.controller.v2;

import barley.wire.wirebarley.application.v2.GlobalAccountAppService;
import barley.wire.wirebarley.application.v2.GlobalTransferAppService;
import barley.wire.wirebarley.presentation.dto.request.GlobalAmountRequest;
import barley.wire.wirebarley.presentation.dto.request.CreateGlobalAccountRequest;
import barley.wire.wirebarley.presentation.dto.request.GlobalTransferRequest;
import barley.wire.wirebarley.presentation.dto.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
@Tag(name = "2. 해외 송금", description = "추가 구현: 실시간 환율 적용 글로벌 송금")
public class GlobalAccountController {

    private final GlobalAccountAppService accountAppService;
    private final GlobalTransferAppService transferAppService;

    @Operation(summary = "글로벌 계좌 생성", description = "통화를 지정하여 새로운 글로벌 계좌를 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "계좌 생성 성공", content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 계좌번호")})
    @PostMapping("/global-accounts")
    public ResponseEntity<AccountResponse> createGlobalAccount(@Valid @RequestBody CreateGlobalAccountRequest request) {
        AccountResponse response = accountAppService.createGlobalAccount(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "글로벌 계좌 삭제(v1과 동일)", description = "ID를 기반으로 글로벌 계좌를 삭제합니다.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "계좌 삭제 성공"),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음")})
    @DeleteMapping("/global-accounts/{accountId}")
    public ResponseEntity<Void> deleteGlobalAccount(@PathVariable Long accountId) {
        accountAppService.deleteGlobalAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "글로벌 계좌 입금 (v2 - 통화 지정)", description = "계좌에 금액을 입금합니다. 계좌 통화와 일치하는 통화로만 입금 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "입금 성공", content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 금액 또는 통화 불일치"),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음")})
    @PostMapping("/global-accounts/{accountId}/deposit")
    public ResponseEntity<BalanceResponse> deposit(@PathVariable Long accountId,
            @Valid @RequestBody GlobalAmountRequest request) {
        BalanceResponse response = accountAppService.globalDeposit(accountId, request.amount());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "출금 (v2 - 통화 지정)", description = "계좌에서 금액을 출금합니다. 계좌 통화와 일치하는 통화로만 출금 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "출금 성공", content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 금액 또는 통화 불일치"),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "잔액 부족"),
            @ApiResponse(responseCode = "422", description = "일일 한도 초과")})
    @PostMapping("/global-accounts/{accountId}/withdraw")
    public ResponseEntity<BalanceResponse> withdraw(@PathVariable Long accountId,
            @Valid @RequestBody GlobalAmountRequest request) {
        BalanceResponse response = accountAppService.withdraw(accountId, request.amount());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "글로벌 송금 (자동 환전)", description = "다른 통화 계좌 간 자동 환전 송금을 실행합니다. (수수료: 이체 1% + 다른 통화 환전 0.5%)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "송금 성공", content = @Content(schema = @Schema(implementation = GlobalTransferResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 통화 불일치"),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "잔액 부족"),
            @ApiResponse(responseCode = "422", description = "일일 한도 초과")})
    @PostMapping("/global-transfers")
    public ResponseEntity<GlobalTransferResponse> globalTransfer(@Valid @RequestBody GlobalTransferRequest request) {
        GlobalTransferResponse response = transferAppService.globalTransfer(request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "글로벌 계좌 상세 조회", description = "ID를 기반으로 글로벌 계좌의 상세 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음")})
    @GetMapping("/global-accounts/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long accountId) {
        AccountResponse response = accountAppService.getAccount(accountId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "글로벌 계좌 잔액 조회", description = "글로벌 계좌의 현재 잔액을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음")})
    @GetMapping("/global-accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable Long accountId) {
        BalanceResponse response = accountAppService.getBalance(accountId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "거래 내역 조회 (v1과 동일)", description = "글로벌 계좌의 거래 내역을 최신순으로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거래 내역 조회 성공", content = @Content(schema = @Schema(implementation = TransactionListResponse.class))),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음")})
    @GetMapping("/global-accounts/{accountId}/transactions")
    public ResponseEntity<TransactionListResponse> getTransactions(@PathVariable Long accountId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        TransactionListResponse response = accountAppService.getTransactions(accountId, pageable);
        return ResponseEntity.ok(response);
    }
}
