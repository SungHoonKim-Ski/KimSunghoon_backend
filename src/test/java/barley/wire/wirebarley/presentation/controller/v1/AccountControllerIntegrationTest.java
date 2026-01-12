package barley.wire.wirebarley.presentation.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import barley.wire.wirebarley.IntegrationTestBase;
import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.util.Map;

import barley.wire.wirebarley.presentation.dto.request.AmountRequest;
import barley.wire.wirebarley.presentation.dto.request.CreateAccountRequest;
import barley.wire.wirebarley.presentation.dto.request.GlobalTransferRequest;
import barley.wire.wirebarley.presentation.dto.request.TransferRequest;
import barley.wire.wirebarley.presentation.dto.response.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AccountControllerIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("계좌 생성 API 테스트")
    void createAccount() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest("110-123-456789", "kim");

        ApiResponse<AccountResponse> response = postAction("/api/v1/accounts", request, AccountResponse.class);

        assertThat(response.status()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(response.body().accountNumber()).isEqualTo("110-123-456789");
        assertThat(response.body().ownerName()).isEqualTo("kim");
        assertThat(response.body().balance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("계좌 생성 후 삭제 API 테스트")
    void deleteAccount() throws Exception {
        AccountResponse accountResponse = fixture.createAccount("110-123-456789", "kim");

        ApiResponse<Void> response = deleteAction("/api/v1/accounts/" + accountResponse.id());

        assertThat(response.status()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    @Test
    @DisplayName("중복된 계좌 번호로 생성 시도 시 실패 테스트")
    void createAccount_DuplicateAccountNumber() throws Exception {
        fixture.createAccount("110-123-456789", "kim");

        CreateAccountRequest request = new CreateAccountRequest("110-123-456789", "lee");

        ApiResponse<ErrorResponse> response = postAction("/api/v1/accounts", request, ErrorResponse.class);

        assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.body().code()).isEqualTo("ALREADY_EXISTS");
    }

    @Test
    @DisplayName("입금 API 테스트")
    void deposit() throws Exception {
        AccountResponse accountResponse = fixture.createAccount("110-123-456789", "kim");

        AmountRequest depositRequest = new AmountRequest(BigDecimal.valueOf(100000));

        ApiResponse<BalanceResponse> response = postAction("/api/v1/accounts/" + accountResponse.id() + "/deposit",
                depositRequest, BalanceResponse.class);

        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body().balance()).isEqualByComparingTo(BigDecimal.valueOf(100000));
    }

    @Test
    @DisplayName("출금 API 테스트")
    void withdraw() throws Exception {
        AccountResponse accountResponse = fixture.createAccount("110-123-456789", "kim");
        fixture.deposit(accountResponse.id(), BigDecimal.valueOf(200000));

        AmountRequest withdrawRequest = new AmountRequest(BigDecimal.valueOf(50000));

        ApiResponse<BalanceResponse> response = postAction("/api/v1/accounts/" + accountResponse.id() + "/withdraw",
                withdrawRequest, BalanceResponse.class);

        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body().balance()).isEqualByComparingTo(BigDecimal.valueOf(150000));
    }

    @Test
    @DisplayName("잔액 부족 시 출금 실패 테스트")
    void withdraw_InsufficientBalance() throws Exception {
        AccountResponse accountResponse = fixture.createAccount("110-123-456789", "kim");

        AmountRequest withdrawRequest = new AmountRequest(BigDecimal.valueOf(10000));

        ApiResponse<ErrorResponse> response = postAction("/api/v1/accounts/" + accountResponse.id() + "/withdraw",
                withdrawRequest, ErrorResponse.class);

        assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.body().code()).isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    @DisplayName("일일 출금 한도 초과 테스트")
    void withdraw_LimitExceeded() throws Exception {
        AccountResponse accountResponse = fixture.createAccount("110-123-456789", "kim");
        fixture.deposit(accountResponse.id(), BigDecimal.valueOf(2000000));

        AmountRequest withdrawRequest = new AmountRequest(BigDecimal.valueOf(1000001));

        ApiResponse<ErrorResponse> response = postAction("/api/v1/accounts/" + accountResponse.id() + "/withdraw",
                withdrawRequest, ErrorResponse.class);

        assertThat(response.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(response.body().code()).isEqualTo("LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("이체 API 테스트 - 수수료 1% 확인")
    void transfer() throws Exception {
        AccountResponse account1 = fixture.createAccount("110-123-456789", "kim");
        fixture.deposit(account1.id(), BigDecimal.valueOf(200000));

        AccountResponse account2 = fixture.createAccount("220-987-654321", "lee");

        TransferRequest transferRequest = new TransferRequest(account1.id(), account2.id(), BigDecimal.valueOf(100000));

        ApiResponse<TransferResponse> response = postAction("/api/v1/transfers", transferRequest,
                TransferResponse.class);

        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body().amount()).isEqualByComparingTo(BigDecimal.valueOf(100000));
        assertThat(response.body().fee()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(response.body().fromBalance()).isEqualByComparingTo(BigDecimal.valueOf(99000));
        assertThat(response.body().toBalance()).isEqualByComparingTo(BigDecimal.valueOf(100000));
    }

    @Test
    @DisplayName("일일 이체 한도 초과 테스트")
    void transfer_LimitExceeded() throws Exception {
        AccountResponse account1 = fixture.createAccount("110-123-456789", "kim");
        fixture.deposit(account1.id(), BigDecimal.valueOf(5000000));

        AccountResponse account2 = fixture.createAccount("220-987-654321", "lee");

        TransferRequest transferRequest = new TransferRequest(account1.id(), account2.id(),
                BigDecimal.valueOf(3000001));

        ApiResponse<ErrorResponse> response = postAction("/api/v1/transfers", transferRequest, ErrorResponse.class);

        assertThat(response.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(response.body().code()).isEqualTo("LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("글로벌 이체 API 테스트")
    void globalTransfer() throws Exception {
        AccountResponse account1 = fixture.createAccount("110-123-456789", "kim");
        fixture.deposit(account1.id(), BigDecimal.valueOf(200000));
        AccountResponse account2 = fixture.createGlobalAccount("220-987-654321", "lee", Currency.USD);

        ExchangeRateApiResponse mockResponse = new ExchangeRateApiResponse("KRW",
                Map.of("USD", BigDecimal.valueOf(0.00075)));
        when(exchangeRateClient.getExchangeRates("KRW")).thenReturn(mockResponse);

        GlobalTransferRequest request = new GlobalTransferRequest(account1.id(), account2.id(),
                BigDecimal.valueOf(100000));

        ApiResponse<GlobalTransferResponse> response = postAction("/api/v2/global-transfers", request,
                GlobalTransferResponse.class);

        // 실제 로직과 동일하게 계산 (실제 환율 사용)
        BigDecimal actualExchangeRate = response.body().exchangeRate();
        BigDecimal transferFeeRate = new BigDecimal("0.01"); // 1% 이체 수수료
        BigDecimal exchangeFeeRate = new BigDecimal("0.005"); // 0.5% 환전 수수료

        BigDecimal amount = BigDecimal.valueOf(100000);
        // 환전
        BigDecimal converted = amount.multiply(actualExchangeRate).setScale(2, java.math.RoundingMode.DOWN);
        // 환전 수수료 차감
        BigDecimal exchangeFee = converted.multiply(exchangeFeeRate).setScale(2, java.math.RoundingMode.UP);
        BigDecimal finalConverted = converted.subtract(exchangeFee);
        // 이체 수수료
        BigDecimal transferFee = amount.multiply(transferFeeRate).setScale(0, java.math.RoundingMode.UP);
        BigDecimal totalWithdraw = amount.add(transferFee);

        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body().originalAmount()).isEqualByComparingTo(amount);
        assertThat(response.body().convertedAmount()).isEqualByComparingTo(finalConverted);
        assertThat(response.body().fromBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(200000).subtract(totalWithdraw));
        assertThat(response.body().toBalance()).isEqualByComparingTo(finalConverted);
    }

    @Test
    @DisplayName("거래내역 조회 API 테스트")
    void getTransactions() throws Exception {
        AccountResponse accountResponse = fixture.createAccount("110-123-456789", "kim");

        fixture.deposit(accountResponse.id(), BigDecimal.valueOf(50000));
        fixture.withdraw(accountResponse.id(), BigDecimal.valueOf(10000));

        ApiResponse<TransactionListResponse> response = getAction(
                "/api/v1/accounts/" + accountResponse.id() + "/transactions", TransactionListResponse.class);

        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body().items()).hasSize(2);

        assertThat(response.body().items().get(0).type()).isEqualTo(TransactionType.WITHDRAW);
        assertThat(response.body().items().get(1).type()).isEqualTo(TransactionType.DEPOSIT);
    }

    @Test
    @DisplayName("거래내역 페이징 테스트")
    void getTransactions_Pagination() throws Exception {
        AccountResponse accountResponse = fixture.createAccount("110-123-456789", "kim");

        for (int i = 1; i <= 15; i++) {
            fixture.deposit(accountResponse.id(), BigDecimal.valueOf(1000 * i));
        }

        ApiResponse<TransactionListResponse> response = performAction(
                get("/api/v1/accounts/" + accountResponse.id() + "/transactions").param("page", "0")
                        .param("size", "10"),
                TransactionListResponse.class);

        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body().items()).hasSize(10);
        // 최신순으로 정렬되었는지 확인 (마지막 입금액 15000이 첫 번째여야 함)
        assertThat(response.body().items().get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        assertThat(response.body().totalElements()).isEqualTo(15);
        assertThat(response.body().totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("잘못된 요청 시 validation 에러 테스트")
    void createAccount_ValidationError() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest("", "");

        ApiResponse<ErrorResponse> response = postAction("/api/v1/accounts", request, ErrorResponse.class);

        assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.body().code()).isEqualTo("INVALID_REQUEST");
    }
}
