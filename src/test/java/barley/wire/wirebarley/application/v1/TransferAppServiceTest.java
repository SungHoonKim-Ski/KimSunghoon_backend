package barley.wire.wirebarley.application.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import barley.wire.wirebarley.application.v1.TransferAppService;
import barley.wire.wirebarley.common.event.TransactionEvent;
import barley.wire.wirebarley.common.exception.InsufficientBalanceException;
import barley.wire.wirebarley.common.exception.InvalidAmountException;
import barley.wire.wirebarley.common.exception.LimitExceededException;
import barley.wire.wirebarley.common.util.MoneyUtils;
import barley.wire.wirebarley.common.validator.AccountValidator;
import barley.wire.wirebarley.domain.account.Account;
import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.infrastructure.service.AccountService;
import barley.wire.wirebarley.presentation.dto.request.TransferRequest;
import barley.wire.wirebarley.presentation.dto.response.TransferResponse;

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
class TransferAppServiceTest {

    @Mock
    private AccountService accountService;

    @Mock
    private AccountValidator accountValidator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TransferAppService transferAppService;

    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        // 출금 계좌 설정 (KRW)
        fromAccount = new Account("110-111-111111", "Sender", Currency.KRW);
        ReflectionTestUtils.setField(fromAccount, "id", 1L);
        fromAccount.deposit(BigDecimal.valueOf(1000000));

        // 입금 계좌 설정 (KRW)
        toAccount = new Account("220-222-222222", "Receiver", Currency.KRW);
        ReflectionTestUtils.setField(toAccount, "id", 2L);
    }

    @Test
    @DisplayName("계좌 이체 성공 (동일 통화 - KRW -> KRW)")
    void transfer_Success() {
        // [given]
        // 100,000 KRW를 동일 통화 계좌로 이체하는 시나리오
        BigDecimal amount = BigDecimal.valueOf(100000);
        TransferRequest request = new TransferRequest(1L, 2L, amount);

        when(accountService.getAccountsWithLockOrdered(1L, 2L)).thenReturn(Pair.of(fromAccount, toAccount));

        // [when]
        TransferResponse response = transferAppService.transfer(request);

        // [then]
        // 이체 로직 검증
        // 1. 이체 수수료: 100,000 * 0.01 = 1,000 KRW
        // 2. 출금 합계: 100,000 + 1,000 = 101,000 KRW
        // 3. 출금 계좌 잔액: 1,000,000 - 101,000 = 899,000 KRW
        // 4. 입금 계좌 잔액: 0 + 100,000 = 100,000 KRW

        BigDecimal expectedFee = MoneyUtils.calculateTransferFee(amount, Currency.KRW);

        assertThat(response.fee()).isEqualByComparingTo(expectedFee);
        assertThat(fromAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(899000));
        assertThat(toAccount.getBalance()).isEqualByComparingTo(amount);

        verify(eventPublisher, times(2)).publishEvent(any(TransactionEvent.class));
    }

    @Test
    @DisplayName("통화가 다른 계좌 간 이체 시도 시 실패 (V1 API)")
    void transfer_Fail_DifferentCurrency() {
        // [given]
        // 출금계좌(KRW)에서 입금계좌(USD)로 V1 이체 시도
        toAccount = new Account("220-222-222222", "Receiver", Currency.USD);
        ReflectionTestUtils.setField(toAccount, "id", 2L);

        TransferRequest request = new TransferRequest(1L, 2L, BigDecimal.valueOf(100000));
        when(accountService.getAccountsWithLockOrdered(1L, 2L)).thenReturn(Pair.of(fromAccount, toAccount));

        // AccountValidator에서 통화 불일치 예외 발생을 모의
        doThrow(new InvalidAmountException("통화가 다른 계좌 간 이체는 불가능합니다"))
                .when(accountValidator).validateSameCurrency(fromAccount, toAccount);

        // [when & then]
        assertThatThrownBy(() -> transferAppService.transfer(request))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("잔액 부족 시 이체 실패")
    void transfer_Fail_InsufficientBalance() {
        // [given]
        BigDecimal amount = BigDecimal.valueOf(2000000); // 잔액(100만)보다 큰 금액
        TransferRequest request = new TransferRequest(1L, 2L, amount);

        when(accountService.getAccountsWithLockOrdered(1L, 2L)).thenReturn(Pair.of(fromAccount, toAccount));

        doThrow(new InsufficientBalanceException("잔액이 부족합니다"))
                .when(accountValidator).validateBalance(any(), any(), anyString());

        // [when & then]
        assertThatThrownBy(() -> transferAppService.transfer(request))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    @DisplayName("이체 한도 초과 시 이체 실패")
    void transfer_Fail_LimitExceeded() {
        // [given]
        BigDecimal amount = BigDecimal.valueOf(5000000); // 500만원 (한도 초과 가정)
        TransferRequest request = new TransferRequest(1L, 2L, amount);

        when(accountService.getAccountsWithLockOrdered(1L, 2L)).thenReturn(Pair.of(fromAccount, toAccount));

        doThrow(new LimitExceededException("일일 이체 한도를 초과했습니다"))
                .when(accountValidator).checkTransferLimit(anyLong(), any());

        // [when & then]
        assertThatThrownBy(() -> transferAppService.transfer(request))
                .isInstanceOf(LimitExceededException.class);
    }
}
