package barley.wire.wirebarley.application.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import barley.wire.wirebarley.application.v2.GlobalTransferAppService;
import barley.wire.wirebarley.common.event.TransactionEvent;
import barley.wire.wirebarley.common.exception.InsufficientBalanceException;
import barley.wire.wirebarley.common.exception.LimitExceededException;
import barley.wire.wirebarley.common.util.MoneyUtils;
import barley.wire.wirebarley.common.validator.AccountValidator;
import barley.wire.wirebarley.domain.account.Account;
import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.infrastructure.service.AccountService;
import barley.wire.wirebarley.infrastructure.service.ExchangeRateService;
import barley.wire.wirebarley.presentation.dto.request.GlobalTransferRequest;
import barley.wire.wirebarley.presentation.dto.response.GlobalTransferResponse;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.util.Pair;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GlobalTransferAppServiceTest {

    @Mock
    private AccountService accountService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @Mock
    private AccountValidator accountValidator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GlobalTransferAppService globalTransferAppService;

    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        // 출금 계좌 설정 (기본 원화)
        fromAccount = new Account("110-111-111111", "Sender", Currency.KRW);
        ReflectionTestUtils.setField(fromAccount, "id", 1L);
        fromAccount.deposit(BigDecimal.valueOf(1000000));

        // 입금 계좌 설정 (기본 달러)
        toAccount = new Account("220-222-222222", "Receiver", Currency.USD);
        ReflectionTestUtils.setField(toAccount, "id", 2L);
    }

    @Test
    @DisplayName("글로벌 송금 성공 (KRW -> USD)")
    void globalTransfer_Success_KRW_to_USD() {
        // [given]
        // 100,000 KRW를 USD로 송금하는 시나리오
        BigDecimal amount = BigDecimal.valueOf(100000);
        BigDecimal exchangeRate = BigDecimal.valueOf(0.00075);

        GlobalTransferRequest request = new GlobalTransferRequest(1L, 2L, amount);

        when(accountService.getAccountsWithLockOrdered(1L, 2L)).thenReturn(Pair.of(fromAccount, toAccount));
        when(exchangeRateService.getExchangeRate(Currency.KRW, Currency.USD)).thenReturn(exchangeRate);
        // 실제 ExchangeRateService.convertAmount 로직을 시뮬레이션
        when(exchangeRateService.convertAmount(any(BigDecimal.class), eq(Currency.KRW), eq(Currency.USD)))
                .thenAnswer(invocation -> ((BigDecimal) invocation.getArgument(0))
                        .multiply(exchangeRate));

        // [when]
        GlobalTransferResponse response = globalTransferAppService.globalTransfer(request);

        // [then]
        // 1. 기초 데이터 검증
        assertThat(response.fromAccountId()).isEqualTo(1L);
        assertThat(response.toAccountId()).isEqualTo(2L);
        assertThat(response.fromCurrency()).isEqualTo(Currency.KRW);
        assertThat(response.toCurrency()).isEqualTo(Currency.USD);
        assertThat(response.exchangeRate()).isEqualByComparingTo(exchangeRate);

        // 2. 수수료 및 출금액 검증 - 이체 수수료 1% 적용
        BigDecimal expectedTransferFee = MoneyUtils.calculateTransferFee(amount, Currency.KRW);
        assertThat(response.fee()).isEqualByComparingTo(expectedTransferFee);

        BigDecimal expectedTotalWithdraw = amount.add(expectedTransferFee);
        assertThat(fromAccount.getBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(1000000).subtract(expectedTotalWithdraw));

        // 3. 환전 및 입금액 검증 - 환전 수수료 0.5% 차감 후 최종 입금
        // 환전액 (100,000 * 0.00075 = 75.00)
        BigDecimal convertedAmount = MoneyUtils.scale(amount.multiply(exchangeRate), Currency.USD);
        BigDecimal expectedExchangeFee = MoneyUtils.calculateExchangeFee(convertedAmount, Currency.USD);
        BigDecimal expectedFinalConverted = MoneyUtils.calculateFinalConverted(convertedAmount,
                expectedExchangeFee,
                Currency.USD);

        assertThat(response.convertedAmount()).isEqualByComparingTo(expectedFinalConverted);
        assertThat(toAccount.getBalance()).isEqualByComparingTo(expectedFinalConverted);

        verify(eventPublisher, times(2)).publishEvent(any(TransactionEvent.class));
    }

    @Test
    @DisplayName("글로벌 송금 성공 (USD -> KRW)")
    void globalTransfer_Success_USD_to_KRW() {
        // [given]
        // 100 USD를 KRW로 송금하는 시나리오
        fromAccount = new Account("110-111-111111", "Sender", Currency.USD);
        ReflectionTestUtils.setField(fromAccount, "id", 1L);
        fromAccount.deposit(BigDecimal.valueOf(1000.00));

        toAccount = new Account("220-222-222222", "Receiver", Currency.KRW);
        ReflectionTestUtils.setField(toAccount, "id", 2L);

        BigDecimal amount = BigDecimal.valueOf(100.00);
        BigDecimal exchangeRate = BigDecimal.valueOf(1400);

        GlobalTransferRequest request = new GlobalTransferRequest(1L, 2L, amount);

        when(accountService.getAccountsWithLockOrdered(1L, 2L)).thenReturn(Pair.of(fromAccount, toAccount));
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.KRW)).thenReturn(exchangeRate);
        when(exchangeRateService.convertAmount(any(BigDecimal.class), any(Currency.class), any(Currency.class)))
                .thenAnswer(invocation -> {
                    BigDecimal a = invocation.getArgument(0);
                    Currency from = invocation.getArgument(1);
                    Currency to = invocation.getArgument(2);
                    if (from == Currency.USD && to == Currency.KRW)
                        return a.multiply(exchangeRate);
                    return a;
                });

        // [when]
        GlobalTransferResponse response = globalTransferAppService.globalTransfer(request);

        // [then]
        // 수수료 및 잔액 계산 검증:
        // 1. USD 이체 수수료: 100.00 * 0.01 = 1.00 USD
        // 2. KRW 환전 수수료: (100.00 * 1400) * 0.005 = 700 KRW
        // 3. 최종 입금액: 140,000 - 700 = 139,300 KRW
        BigDecimal expectedTransferFee = MoneyUtils.calculateTransferFee(amount, Currency.USD);
        BigDecimal convertedAmount = MoneyUtils.scale(amount.multiply(exchangeRate), Currency.KRW);
        BigDecimal expectedExchangeFee = MoneyUtils.calculateExchangeFee(convertedAmount, Currency.KRW);
        BigDecimal expectedFinalAmount = MoneyUtils.calculateFinalConverted(convertedAmount,
                expectedExchangeFee,
                Currency.KRW);

        assertThat(response.fee()).isEqualByComparingTo(expectedTransferFee);
        assertThat(fromAccount.getBalance())
                .isEqualByComparingTo(
                        BigDecimal.valueOf(1000.00).subtract(amount.add(expectedTransferFee)));
        assertThat(toAccount.getBalance()).isEqualByComparingTo(expectedFinalAmount);
    }

    @Test
    @DisplayName("글로벌 송금 성공 (USD -> EUR: 원화 이외 통화 간 송금)")
    void globalTransfer_Success_NonKRW_to_NonKRW() {
        // [given]
        fromAccount = new Account("110-111-111111", "Sender", Currency.USD);
        ReflectionTestUtils.setField(fromAccount, "id", 1L);
        fromAccount.deposit(BigDecimal.valueOf(1000.00));

        toAccount = new Account("330-333-333333", "Receiver", Currency.EUR);
        ReflectionTestUtils.setField(toAccount, "id", 3L);

        BigDecimal amount = BigDecimal.valueOf(100.00);
        BigDecimal exchangeRate = BigDecimal.valueOf(0.9); // 1 USD = 0.9 EUR

        GlobalTransferRequest request = new GlobalTransferRequest(1L, 3L, amount);

        when(accountService.getAccountsWithLockOrdered(1L, 3L)).thenReturn(Pair.of(fromAccount, toAccount));
        when(exchangeRateService.getExchangeRate(any(Currency.class), any(Currency.class)))
                .thenReturn(exchangeRate);
        when(exchangeRateService.convertAmount(any(BigDecimal.class), any(Currency.class), any(Currency.class)))
                .thenAnswer(inv -> ((BigDecimal) inv.getArgument(0)).multiply(exchangeRate));

        // [when]
        GlobalTransferResponse response = globalTransferAppService.globalTransfer(request);

        // [then]
        // 1. 환전액: 100.00 * 0.9 = 90.00 EUR
        // 2. 환전 수수료: 90.00 * 0.005 = 0.45 EUR
        // 3. 최종 EUR 입급: 90.00 - 0.45 = 89.55 EUR
        BigDecimal convertedAmount = MoneyUtils.scale(amount.multiply(exchangeRate), Currency.EUR);
        BigDecimal expectedExchangeFee = MoneyUtils.calculateExchangeFee(convertedAmount, Currency.EUR);
        BigDecimal expectedFinalAmount = MoneyUtils.calculateFinalConverted(convertedAmount,
                expectedExchangeFee,
                Currency.EUR);

        assertThat(response.toCurrency()).isEqualTo(Currency.EUR);
        assertThat(toAccount.getBalance()).isEqualByComparingTo(expectedFinalAmount);
    }

    @Test
    @DisplayName("글로벌 송금 성공 (동일 통화 - KRW -> KRW)")
    void globalTransfer_Success_SameCurrency() {
        // [given]
        toAccount = new Account("220-222-222222", "Receiver", Currency.KRW);
        ReflectionTestUtils.setField(toAccount, "id", 2L);

        BigDecimal amount = BigDecimal.valueOf(100000);
        GlobalTransferRequest request = new GlobalTransferRequest(1L, 2L, amount);

        when(accountService.getAccountsWithLockOrdered(1L, 2L)).thenReturn(Pair.of(fromAccount, toAccount));
        when(exchangeRateService.getExchangeRate(Currency.KRW, Currency.KRW)).thenReturn(BigDecimal.ONE);
        when(exchangeRateService.convertAmount(any(BigDecimal.class), any(Currency.class), any(Currency.class)))
                .thenAnswer(i -> i.getArgument(0));

        // [when]
        GlobalTransferResponse response = globalTransferAppService.globalTransfer(request);

        // [then]
        assertThat(response.fromCurrency()).isEqualTo(Currency.KRW);
        assertThat(response.toCurrency()).isEqualTo(Currency.KRW);

        // 동일 통화 간 이체 시에는 환전 보수가 발생하지 않습니다
        assertThat(toAccount.getBalance()).isEqualByComparingTo(amount);
        assertThat(response.convertedAmount()).isEqualByComparingTo(amount);
    }

    @Test
    @DisplayName("잔액 부족 시 글로벌 송금 실패")
    void globalTransfer_Fail_InsufficientBalance() {
        // [given]
        fromAccount.withdraw(fromAccount.getBalance());
        BigDecimal amount = BigDecimal.valueOf(100000);
        GlobalTransferRequest request = new GlobalTransferRequest(1L, 2L, amount);

        when(accountService.getAccountsWithLockOrdered(1L, 2L)).thenReturn(Pair.of(fromAccount, toAccount));
        when(exchangeRateService.getExchangeRate(any(), any())).thenReturn(BigDecimal.ONE);
        when(exchangeRateService.convertAmount(any(), any(), any())).thenAnswer(i -> i.getArgument(0));

        doThrow(new InsufficientBalanceException("잔액이 부족합니다"))
                .when(accountValidator).validateBalance(any(), any(), anyString());

        // [when & then]
        assertThatThrownBy(() -> globalTransferAppService.globalTransfer(request))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    @DisplayName("잘못된 송금 금액 시 글로벌 송금 실패")
    void globalTransfer_Fail_InvalidAmount() {
        // [given]
        BigDecimal amount = BigDecimal.valueOf(-100);
        GlobalTransferRequest request = new GlobalTransferRequest(1L, 2L, amount);

        doThrow(new barley.wire.wirebarley.common.exception.InvalidAmountException("금액은 0보다 커야 합니다"))
                .when(accountValidator).validateAmount(amount);

        // [when & then]
        assertThatThrownBy(() -> globalTransferAppService.globalTransfer(request))
                .isInstanceOf(barley.wire.wirebarley.common.exception.InvalidAmountException.class);
    }

    @Test
    @DisplayName("계좌를 찾을 수 없을 때 글로벌 송금 실패")
    void globalTransfer_Fail_AccountNotFound() {
        // [given]
        GlobalTransferRequest request = new GlobalTransferRequest(1L, 99L, BigDecimal.valueOf(100));

        when(accountService.getAccountsWithLockOrdered(1L, 99L))
                .thenThrow(new barley.wire.wirebarley.common.exception.AccountNotFoundException(
                        "계좌를 찾을 수 없습니다"));

        // [when & then]
        assertThatThrownBy(() -> globalTransferAppService.globalTransfer(request))
                .isInstanceOf(barley.wire.wirebarley.common.exception.AccountNotFoundException.class);
    }

    @Test
    @DisplayName("비원화 계좌 송금 시 KRW 환산 한도 초과 검증 (USD -> USD)")
    void globalTransfer_Fail_LimitExceeded_WithConversion() {
        // [given]
        // 2,500 USD 송금 시도 (환율 1400 적용 시 3,500,000 KRW로 이체 한도 300만 초과)
        fromAccount = new Account("110-USD-001", "Sender", Currency.USD);
        ReflectionTestUtils.setField(fromAccount, "id", 1L);
        fromAccount.deposit(BigDecimal.valueOf(10000));

        toAccount = new Account("220-USD-002", "Receiver", Currency.USD);
        ReflectionTestUtils.setField(toAccount, "id", 2L);

        BigDecimal amount = BigDecimal.valueOf(2500);

        GlobalTransferRequest request = new GlobalTransferRequest(1L, 2L, amount);

        when(accountService.getAccountsWithLockOrdered(1L, 2L)).thenReturn(Pair.of(fromAccount, toAccount));
        when(exchangeRateService.getExchangeRate(any(), any())).thenReturn(BigDecimal.ONE);

        // 1. 송금액 환전 (USD -> USD)
        // AppService에서 amount(2500)를 그대로 전달함
        doReturn(amount).when(exchangeRateService).convertAmount(eq(amount), eq(Currency.USD),
                eq(Currency.USD));

        // 3. 출금 한도 체크를 위한 KRW 환산 (수수료 포함)
        // 2500 USD + 1% fee(25.00) = 2525.00 USD

        // Validator에서 KRW로 환산된 금액이 아니라 전달된 원본 금액(2,500)으로 호출되는지 확인
        doThrow(new LimitExceededException("이체 한도 초과"))
                .when(accountValidator)
                .checkGlobalTransferLimit(eq(1L), eq(amount));

        // [when & then]
        assertThatThrownBy(() -> globalTransferAppService.globalTransfer(request))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    @DisplayName("비원화 계좌 송금 시 수수료 포함 KRW 환산 출금 한도 초과 검증 (USD -> USD)")
    void globalTransfer_Fail_WithdrawLimitExceeded_WithConversion() {
        // [given]
        // 700 USD 송금 (수수료 1% 포함 707.00 USD)
        // 환율 1500 적용 시 1,060,500 KRW로 출금 한도 100만 초과
        fromAccount = new Account("110-USD-001", "Sender", Currency.USD);
        ReflectionTestUtils.setField(fromAccount, "id", 1L);
        fromAccount.deposit(BigDecimal.valueOf(10000));

        toAccount = new Account("220-USD-002", "Receiver", Currency.USD);
        ReflectionTestUtils.setField(toAccount, "id", 2L);

        BigDecimal amount = BigDecimal.valueOf(700);
        BigDecimal totalWithdrawUSD = new BigDecimal("707.00"); // 700 + 7.00(fee)

        GlobalTransferRequest request = new GlobalTransferRequest(1L, 2L, amount);

        when(accountService.getAccountsWithLockOrdered(1L, 2L)).thenReturn(Pair.of(fromAccount, toAccount));
        when(exchangeRateService.getExchangeRate(any(), any())).thenReturn(BigDecimal.ONE);

        // 다양한 환산 케이스 stubbing
        doReturn(amount).when(exchangeRateService).convertAmount(eq(amount), eq(Currency.USD),
                eq(Currency.USD));

        // Validator에서 환산된 총 출금액이 아니라 전달된 원본 금액(707.00)으로 호출되는지 검증
        doThrow(new LimitExceededException("출금 한도 초과"))
                .when(accountValidator).checkWithdrawLimit(eq(fromAccount), eq(totalWithdrawUSD));

        // [when & then]
        assertThatThrownBy(() -> globalTransferAppService.globalTransfer(request))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    @DisplayName("원화가 아닌 통화 간 송금 시에도 KRW 기준으로 한도 검증이 수행되는지 확인 (EUR -> USD)")
    void globalTransfer_Fail_LimitExceeded_EUR_to_USD_WithKRWConversion() {
        // [given]
        // 2,000 EUR 송금 시도
        fromAccount = new Account("110-EUR-001", "Sender", Currency.EUR);
        ReflectionTestUtils.setField(fromAccount, "id", 1L);
        fromAccount.deposit(BigDecimal.valueOf(10000));

        toAccount = new Account("220-USD-001", "Receiver", Currency.USD);
        ReflectionTestUtils.setField(toAccount, "id", 2L);

        BigDecimal amount = BigDecimal.valueOf(2000);
        // 1 EUR = 1600 KRW 가정 -> 2000 EUR = 3,200,000 KRW (300만 한도 초과)

        GlobalTransferRequest request = new GlobalTransferRequest(1L, 2L, amount);

        when(accountService.getAccountsWithLockOrdered(1L, 2L)).thenReturn(Pair.of(fromAccount, toAccount));

        // EUR -> USD 환율 (한도와 무관하게 1.1 가정)
        when(exchangeRateService.getExchangeRate(Currency.EUR, Currency.USD)).thenReturn(new BigDecimal("1.1"));
        doReturn(amount.multiply(new BigDecimal("1.1"))).when(exchangeRateService).convertAmount(amount,
                Currency.EUR, Currency.USD);

        // 수수료 포함 출금액 환산 (2000 EUR + 1%(20.00) = 2020.00 EUR)

        // 300만 이체 한도 초과 시뮬레이션
        doThrow(new LimitExceededException("이체 한도 초과"))
                .when(accountValidator).checkGlobalTransferLimit(eq(1L), eq(amount));

        // [when & then]
        assertThatThrownBy(() -> globalTransferAppService.globalTransfer(request))
                .isInstanceOf(LimitExceededException.class);

        // Validator 호출 시 원본 EUR 금액(2000)이 사용되었는지 검증
        verify(accountValidator).checkGlobalTransferLimit(eq(1L), eq(amount));
    }
}
