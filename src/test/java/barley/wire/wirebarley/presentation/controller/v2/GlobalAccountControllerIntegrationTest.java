package barley.wire.wirebarley.presentation.controller.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import barley.wire.wirebarley.IntegrationTestBase;
import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.domain.transaction.TransactionType;
import barley.wire.wirebarley.presentation.dto.request.CreateGlobalAccountRequest;
import barley.wire.wirebarley.presentation.dto.request.GlobalAmountRequest;
import barley.wire.wirebarley.presentation.dto.request.GlobalTransferRequest;
import barley.wire.wirebarley.presentation.dto.response.*;
import barley.wire.wirebarley.infrastructure.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class GlobalAccountControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @BeforeEach
    void clearExchangeRateCache() {
        exchangeRateRepository.deleteAll();
    }

    @Test
    @DisplayName("글로벌 계좌 생성 API 테스트")
    void createGlobalAccount() throws Exception {
        CreateGlobalAccountRequest request = new CreateGlobalAccountRequest("220-111-222222", "lee", Currency.USD);

        ApiResponse<AccountResponse> response = postAction("/api/v2/global-accounts", request, AccountResponse.class);

        assertThat(response.status()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(response.body().accountNumber()).isEqualTo("220-111-222222");
        assertThat(response.body().currency()).isEqualTo(Currency.USD);
    }

    @Test
    @DisplayName("글로벌 계좌 입금 및 출금 API 테스트")
    void depositAndWithdraw() throws Exception {
        // [given]
        AccountResponse account = fixture.createGlobalAccount("220-333-444444", "park", Currency.EUR);

        // [when - Deposit]
        GlobalAmountRequest depositRequest = new GlobalAmountRequest(BigDecimal.valueOf(1000));
        ApiResponse<BalanceResponse> depositResponse = postAction(
                "/api/v2/global-accounts/" + account.id() + "/deposit",
                depositRequest, BalanceResponse.class);

        // [then - Deposit]
        assertThat(depositResponse.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(depositResponse.body().balance()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(depositResponse.body().currency()).isEqualTo(Currency.EUR);

        // [when - Withdraw]
        GlobalAmountRequest withdrawRequest = new GlobalAmountRequest(BigDecimal.valueOf(400));
        ApiResponse<BalanceResponse> withdrawResponse = postAction(
                "/api/v2/global-accounts/" + account.id() + "/withdraw",
                withdrawRequest, BalanceResponse.class);

        // [then - Withdraw]
        assertThat(withdrawResponse.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(withdrawResponse.body().balance()).isEqualByComparingTo(BigDecimal.valueOf(600));
    }

    @Test
    @DisplayName("글로벌 송금 API 통합 테스트 (USD -> KRW)")
    void globalTransfer_USD_to_KRW() throws Exception {
        // [given]
        AccountResponse usdAccount = fixture.createGlobalAccount("220-USD-001", "Sender", Currency.USD);
        // 입금 (USD)
        postAction("/api/v2/global-accounts/" + usdAccount.id() + "/deposit",
                new GlobalAmountRequest(BigDecimal.valueOf(1000)), BalanceResponse.class);

        AccountResponse krwAccount = fixture.createAccount("110-KRW-001", "Receiver");

        ExchangeRateApiResponse mockExchangeRate = new ExchangeRateApiResponse("USD",
                Map.of("KRW", BigDecimal.valueOf(1400)));
        when(exchangeRateClient.getExchangeRates("USD")).thenReturn(mockExchangeRate);

        GlobalTransferRequest request = new GlobalTransferRequest(usdAccount.id(), krwAccount.id(),
                BigDecimal.valueOf(100));

        // [when]
        ApiResponse<GlobalTransferResponse> response = postAction("/api/v2/global-transfers", request,
                GlobalTransferResponse.class);

        // [then]
        // 1. 달러 수수료: 100 * 0.01 = 1 USD
        // 2. 환전: 100 * 1400 = 140,000 KRW
        // 3. 환전 수수료: 140,000 * 0.005 = 700 KRW
        // 4. 최종 입금: 140,000 - 700 = 139,300 KRW
        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body().exchangeRate()).isEqualByComparingTo(BigDecimal.valueOf(1400));
        assertThat(response.body().fee()).isEqualByComparingTo(BigDecimal.valueOf(1));
        assertThat(response.body().convertedAmount()).isEqualByComparingTo(BigDecimal.valueOf(139300));
        assertThat(response.body().fromBalance()).isEqualByComparingTo(BigDecimal.valueOf(899));
        assertThat(response.body().toBalance()).isEqualByComparingTo(BigDecimal.valueOf(139300));
    }

    @Test
    @DisplayName("글로벌 계좌 거래 내역 조회 및 정렬 확인")
    void getTransactions_Global() throws Exception {
        // [given]
        AccountResponse account = fixture.createGlobalAccount("220-TX-V2", "User", Currency.USD);

        // 1. 입금 100
        postAction("/api/v2/global-accounts/" + account.id() + "/deposit",
                new GlobalAmountRequest(BigDecimal.valueOf(100)), BalanceResponse.class);
        // 2. 출금 30
        postAction("/api/v2/global-accounts/" + account.id() + "/withdraw",
                new GlobalAmountRequest(BigDecimal.valueOf(30)), BalanceResponse.class);

        // [when]
        ApiResponse<TransactionListResponse> response = getAction(
                "/api/v2/global-accounts/" + account.id() + "/transactions", TransactionListResponse.class);

        // [then]
        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body().items()).hasSize(2);
        // 최신순 정렬 확인 (출금이 첫 번째여야 함)
        assertThat(response.body().items().get(0).type()).isEqualTo(TransactionType.WITHDRAW);
        assertThat(response.body().items().get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(30));
        assertThat(response.body().items().get(1).type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.body().items().get(1).amount()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("글로벌 계좌 삭제 API 테스트")
    void deleteGlobalAccount() throws Exception {
        // [given]
        AccountResponse account = fixture.createGlobalAccount("220-DEL-V2", "User", Currency.EUR);

        // [when]
        ApiResponse<Void> response = deleteAction("/api/v2/global-accounts/" + account.id());

        // [then]
        assertThat(response.status()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }
}
