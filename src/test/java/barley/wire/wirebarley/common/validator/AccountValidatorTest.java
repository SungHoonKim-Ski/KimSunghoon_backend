package barley.wire.wirebarley.common.validator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import barley.wire.wirebarley.common.exception.LimitExceededException;
import barley.wire.wirebarley.domain.account.Account;
import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.domain.transaction.TransactionType;
import barley.wire.wirebarley.infrastructure.repository.TransactionRepository;
import barley.wire.wirebarley.infrastructure.service.AccountService;
import barley.wire.wirebarley.infrastructure.service.ExchangeRateService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountValidatorTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private AccountValidator accountValidator;

    private Account krwAccount;
    private Account usdAccount;

    @BeforeEach
    void setUp() {
        krwAccount = new Account("111-111", "KRW_USER", Currency.KRW);
        ReflectionTestUtils.setField(krwAccount, "id", 1L);

        usdAccount = new Account("222-222", "USD_USER", Currency.USD);
        ReflectionTestUtils.setField(usdAccount, "id", 2L);
    }

    @Test
    @DisplayName("원화 계좌 출금 한도 초과 테스트 (100만원 초과)")
    void checkWithdrawLimit_KRW_Exceeded() {
        when(accountService.getAccount(1L)).thenReturn(krwAccount);
        when(transactionRepository.sumAmountByAccountIdAndTypeAndCreatedAtAfter(eq(1L),
                eq(TransactionType.WITHDRAW), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("500000"));

        assertThatThrownBy(() -> accountValidator.checkWithdrawLimit(1L, new BigDecimal("500001")))
                .isInstanceOf(LimitExceededException.class)
                .hasMessageContaining("일일 출금 한도를 초과했습니다 (한도: 100만원)");
    }

    @Test
    @DisplayName("외화(USD) 계좌 출금 한도 초과 테스트 (환산 시 100만원 초과)")
    void checkWithdrawLimit_USD_Exceeded() {
        when(accountService.getAccount(2L)).thenReturn(usdAccount);
        // 오늘 이미 200 USD 출금 (환율 1400 가정 시 28만원)
        when(transactionRepository.sumAmountByAccountIdAndTypeAndCreatedAtAfter(eq(2L),
                eq(TransactionType.WITHDRAW), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("200"));

        // 현재 600 USD 추가 출금 시도 (총 800 USD = 112만원)
        BigDecimal amount = new BigDecimal("600");
        BigDecimal totalUSD = new BigDecimal("800");
        BigDecimal totalKRW = new BigDecimal("1120000");

        when(exchangeRateService.convertAmount(totalUSD, Currency.USD, Currency.KRW))
                .thenReturn(totalKRW);

        assertThatThrownBy(() -> accountValidator.checkWithdrawLimit(2L, amount))
                .isInstanceOf(LimitExceededException.class)
                .hasMessageContaining("일일 출금 한도를 초과했습니다 (한도: 100만원)");
    }

    @Test
    @DisplayName("원화 계좌 이체 한도 초과 테스트 (300만원 초과)")
    void checkTransferLimit_KRW_Exceeded() {
        when(accountService.getAccount(1L)).thenReturn(krwAccount);
        when(transactionRepository.sumAmountByAccountIdAndTypeAndCreatedAtAfter(eq(1L),
                eq(TransactionType.TRANSFER_OUT), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("2000000"));

        assertThatThrownBy(() -> accountValidator.checkTransferLimit(1L, new BigDecimal("1000001")))
                .isInstanceOf(LimitExceededException.class)
                .hasMessageContaining("일일 이체 한도를 초과했습니다 (한도: 300만원)");
    }

    @Test
    @DisplayName("외화(USD) 계좌 이체 한도 초과 테스트 (환산 시 300만원 초과)")
    void checkTransferLimit_USD_Exceeded() {
        when(accountService.getAccount(2L)).thenReturn(usdAccount);
        // 오늘 이미 1500 USD 이체 (210만원)
        when(transactionRepository.sumAmountByAccountIdAndTypeAndCreatedAtAfter(eq(2L),
                eq(TransactionType.TRANSFER_OUT), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("1500"));

        // 추가로 1000 USD 이체 시도 (총 2500 USD = 350만원)
        BigDecimal amount = new BigDecimal("1000");
        BigDecimal totalUSD = new BigDecimal("2500");
        BigDecimal totalKRW = new BigDecimal("3500000");

        when(exchangeRateService.convertAmount(totalUSD, Currency.USD, Currency.KRW))
                .thenReturn(totalKRW);

        assertThatThrownBy(() -> accountValidator.checkTransferLimit(2L, amount))
                .isInstanceOf(LimitExceededException.class)
                .hasMessageContaining("일일 이체 한도를 초과했습니다 (한도: 300만원)");
    }
}
