package barley.wire.wirebarley.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;

import barley.wire.wirebarley.IntegrationTestBase;
import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.presentation.dto.response.AccountResponse;
import barley.wire.wirebarley.presentation.dto.response.BalanceResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BalanceInquiryIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("v1 국내 계좌 상세 및 잔액 조회 테스트")
    void v1_balanceInquiry() throws Exception {
        // [given] 계좌 생성 및 입금
        AccountResponse account = fixture.createAccount("111-222-333", "User1");
        fixture.deposit(account.id(), BigDecimal.valueOf(100000));

        // [when] 상세 조회
        ApiResponse<AccountResponse> accountDetails = getAction("/api/v1/accounts/" + account.id(),
                AccountResponse.class);

        // [then]
        assertThat(accountDetails.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(accountDetails.body().balance()).isEqualByComparingTo(BigDecimal.valueOf(100000));
        assertThat(accountDetails.body().ownerName()).isEqualTo("User1");

        // [when] 잔액만 조회
        ApiResponse<BalanceResponse> balanceOnly = getAction("/api/v1/accounts/" + account.id() + "/balance",
                BalanceResponse.class);

        // [then]
        assertThat(balanceOnly.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(balanceOnly.body().balance()).isEqualByComparingTo(BigDecimal.valueOf(100000));
    }

    @Test
    @DisplayName("v2 글로벌 계좌 상세 및 잔액 조회 테스트")
    void v2_balanceInquiry() throws Exception {
        // [given] 글로벌 계좌 생성 및 입금
        AccountResponse account = fixture.createGlobalAccount("999-888-777", "GlobalUser", Currency.USD);
        fixture.deposit(account.id(), BigDecimal.valueOf(500.55));

        // [when] 상세 조회
        ApiResponse<AccountResponse> accountDetails = getAction("/api/v2/global-accounts/" + account.id(),
                AccountResponse.class);

        // [then]
        assertThat(accountDetails.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(accountDetails.body().balance()).isEqualByComparingTo(BigDecimal.valueOf(500.55));
        assertThat(accountDetails.body().currency()).isEqualTo(Currency.USD);

        // [when] 잔액만 조회
        ApiResponse<BalanceResponse> balanceOnly = getAction("/api/v2/global-accounts/" + account.id() + "/balance",
                BalanceResponse.class);

        // [then]
        assertThat(balanceOnly.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(balanceOnly.body().balance()).isEqualByComparingTo(BigDecimal.valueOf(500.55));
        assertThat(balanceOnly.body().currency()).isEqualTo(Currency.USD);
    }
}
