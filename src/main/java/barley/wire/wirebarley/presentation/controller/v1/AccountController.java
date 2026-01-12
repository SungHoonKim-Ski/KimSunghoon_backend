package barley.wire.wirebarley.presentation.controller.v1;

import barley.wire.wirebarley.application.v1.AccountAppService;
import barley.wire.wirebarley.application.v1.TransferAppService;
import barley.wire.wirebarley.presentation.dto.request.AmountRequest;
import barley.wire.wirebarley.presentation.dto.request.CreateAccountRequest;
import barley.wire.wirebarley.presentation.dto.request.TransferRequest;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "1. 계좌 및 거래", description = "필수 과제: 계좌 관리, 입출금 및 국내 송금")
public class AccountController {

    private final AccountAppService accountAppService;
    private final TransferAppService transferAppService;

    @Operation(summary = "계좌 생성", description = "새로운 계좌를 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "계좌 생성 성공", content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 계좌번호") })
    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = accountAppService.createAccount(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "계좌 삭제", description = "ID를 기반으로 계좌를 삭제합니다.(soft-delete)")
    @ApiResponses({ @ApiResponse(responseCode = "204", description = "계좌 삭제 성공"),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음") })
    @DeleteMapping("/accounts/{accountId}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long accountId) {
        accountAppService.deleteAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "입금", description = "계좌에 금액을 입금합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "입금 성공", content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 금액"),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음") })
    @PostMapping("/accounts/{accountId}/deposit")
    public ResponseEntity<BalanceResponse> deposit(@PathVariable Long accountId,
            @Valid @RequestBody AmountRequest request) {
        BalanceResponse response = accountAppService.deposit(accountId, request.amount());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "출금", description = "계좌에서 금액을 출금합니다. (일일 한도: 1,000,000원)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "출금 성공", content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 금액"),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "잔액 부족"),
            @ApiResponse(responseCode = "422", description = "일일 한도 초과") })
    @PostMapping("/accounts/{accountId}/withdraw")
    public ResponseEntity<BalanceResponse> withdraw(@PathVariable Long accountId,
            @Valid @RequestBody AmountRequest request) {
        BalanceResponse response = accountAppService.withdraw(accountId, request.amount());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "이체", description = "계좌 간 송금을 실행합니다. (수수료: 1%, 일일 한도: 3,000,000원)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이체 성공", content = @Content(schema = @Schema(implementation = TransferResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "잔액 부족"),
            @ApiResponse(responseCode = "422", description = "일일 한도 초과") })
    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        TransferResponse response = transferAppService.transfer(request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "계좌 상세 조회", description = "ID를 기반으로 계좌의 상세 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음") })
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long accountId) {
        AccountResponse response = accountAppService.getAccount(accountId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "잔액 조회", description = "계좌의 현재 잔액을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음") })
    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable Long accountId) {
        BalanceResponse response = accountAppService.getBalance(accountId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "거래 내역 조회", description = "계좌의 거래 내역을 최신순으로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거래 내역 조회 성공", content = @Content(schema = @Schema(implementation = TransactionListResponse.class))),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음") })
    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<TransactionListResponse> getTransactions(@PathVariable Long accountId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        TransactionListResponse response = accountAppService.getTransactions(accountId, pageable);
        return ResponseEntity.ok(response);
    }
}
