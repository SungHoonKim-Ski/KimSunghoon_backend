package barley.wire.wirebarley.application.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import barley.wire.wirebarley.application.v1.AccountAppService;
import barley.wire.wirebarley.common.event.TransactionEvent;
import barley.wire.wirebarley.common.exception.InvalidAmountException;
import barley.wire.wirebarley.common.exception.LimitExceededException;
import barley.wire.wirebarley.common.validator.AccountValidator;
import barley.wire.wirebarley.domain.account.Account;
import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.domain.transaction.Transaction;
import barley.wire.wirebarley.domain.transaction.TransactionType;
import barley.wire.wirebarley.infrastructure.repository.TransactionRepository;
import barley.wire.wirebarley.infrastructure.service.AccountService;
import barley.wire.wirebarley.presentation.dto.request.CreateAccountRequest;
import barley.wire.wirebarley.presentation.dto.response.AccountResponse;
import barley.wire.wirebarley.presentation.dto.response.BalanceResponse;
import barley.wire.wirebarley.presentation.dto.response.TransactionListResponse;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountAppServiceTest {

    @Mock
    private AccountService accountService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountValidator accountValidator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AccountAppService accountAppService;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        testAccount = new Account("110-123-456789", "kim", Currency.KRW);
        ReflectionTestUtils.setField(testAccount, "id", 1L);
    }

    @Test
    @DisplayName("계좌 생성 성공")
    void createAccount_Success() {
        // [given]
        CreateAccountRequest request = new CreateAccountRequest("110-123-456789", "kim");
        when(accountService.save(any(Account.class))).thenReturn(testAccount);

        // [when]
        AccountResponse response = accountAppService.createAccount(request);

        // [then]
        assertThat(response.accountNumber()).isEqualTo("110-123-456789");
        assertThat(response.ownerName()).isEqualTo("kim");
        assertThat(response.currency()).isEqualTo(Currency.KRW);
        verify(accountValidator).validateDuplicateAccountNumber(eq("110-123-456789"));
        verify(accountService, times(1)).save(any(Account.class));
    }

    @Test
    @DisplayName("입금 성공")
    void deposit_Success() {
        // [given]
        BigDecimal amount = BigDecimal.valueOf(50000);
        when(accountService.getAccountWithLock(1L)).thenReturn(testAccount);

        // [when]
        BalanceResponse response = accountAppService.deposit(1L, amount);

        // [then]
        // 입금 후 잔액 및 이벤트 발생 여부 확인
        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.balance()).isEqualByComparingTo(amount);
        assertThat(response.currency()).isEqualTo(Currency.KRW);

        ArgumentCaptor<TransactionEvent> eventCaptor = ArgumentCaptor.forClass(TransactionEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TransactionEvent event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(event.amount()).isEqualByComparingTo(amount);
        assertThat(event.balanceSnapshot()).isEqualByComparingTo(amount);
        assertThat(event.currency()).isEqualTo(Currency.KRW);
    }

    @Test
    @DisplayName("출금 성공")
    void withdraw_Success() {
        // [given]
        testAccount.deposit(BigDecimal.valueOf(100000));
        BigDecimal amount = BigDecimal.valueOf(10000);

        when(accountService.getAccountWithLock(1L)).thenReturn(testAccount);

        // [when]
        BalanceResponse response = accountAppService.withdraw(1L, amount);

        // [then]
        // 출금 후 잔액(10만 - 1만 = 9만) 확인
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.valueOf(90000));

        verify(accountValidator).checkWithdrawLimit(1L, amount);
        verify(accountValidator).validateBalance(eq(testAccount), eq(amount), anyString());
        verify(eventPublisher).publishEvent(any(TransactionEvent.class));
    }

    @Test
    @DisplayName("거래내역 조회 성공")
    void getTransactions_Success() {
        // [given]
        Transaction tx1 = new Transaction(1L, TransactionType.DEPOSIT, BigDecimal.valueOf(50000), BigDecimal.ZERO,
                BigDecimal.valueOf(50000), null, Currency.KRW);
        Transaction tx2 = new Transaction(1L, TransactionType.WITHDRAW, BigDecimal.valueOf(10000), BigDecimal.ZERO,
                BigDecimal.valueOf(40000), null, Currency.KRW);

        Page<Transaction> transactionPage = new PageImpl<>(List.of(tx2, tx1), PageRequest.of(0, 10), 2);

        when(transactionRepository.findByAccountIdOrderByCreatedAtDescIdDesc(eq(1L), any(Pageable.class)))
                .thenReturn(transactionPage);

        // [when]
        TransactionListResponse response = accountAppService.getTransactions(1L, PageRequest.of(0, 10));

        // [then]
        // 반환된 목록의 첫 번째 항목이 더 최신 거래(tx2)인지 확인하여 정렬 순서 검증
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).type()).isEqualTo(TransactionType.WITHDRAW);
        assertThat(response.items().get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(response.items().get(1).type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.items().get(1).amount()).isEqualByComparingTo(BigDecimal.valueOf(50000));

        assertThat(response.totalElements()).isEqualTo(2);
        verify(accountValidator).validateAccountExistence(1L);
    }

    @Test
    @DisplayName("계좌 삭제 성공")
    void deleteAccount_Success() {
        // [given]
        when(accountService.getAccount(1L)).thenReturn(testAccount);

        // [when]
        accountAppService.deleteAccount(1L);

        // [then]
        verify(accountService).delete(testAccount);
    }

    @Test
    @DisplayName("출금 한도 초과 시 실패")
    void withdraw_Fail_LimitExceeded() {
        // [given]
        BigDecimal amount = BigDecimal.valueOf(1000001);
        when(accountService.getAccountWithLock(1L)).thenReturn(testAccount);
        doThrow(new LimitExceededException("일일 출금 한도를 초과했습니다"))
                .when(accountValidator).checkWithdrawLimit(1L, amount);

        // [when & then]
        assertThatThrownBy(() -> accountAppService.withdraw(1L, amount))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    @DisplayName("잘못된 금액 입금 시도 시 실패")
    void deposit_Fail_InvalidAmount() {
        // [given]
        BigDecimal amount = BigDecimal.valueOf(-100);
        doThrow(new InvalidAmountException("금액은 0보다 커야 합니다"))
                .when(accountValidator).validateAmount(amount);

        // [when & then]
        assertThatThrownBy(() -> accountAppService.deposit(1L, amount))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("존재하지 않는 계좌 거래내역 조회 시 실패")
    void getTransactions_Fail_AccountNotFound() {
        // [given]
        doThrow(new barley.wire.wirebarley.common.exception.AccountNotFoundException("계좌를 찾을 수 없음"))
                .when(accountValidator).validateAccountExistence(99L);

        // [when & then]
        assertThatThrownBy(() -> accountAppService.getTransactions(99L, PageRequest.of(0, 10)))
                .isInstanceOf(barley.wire.wirebarley.common.exception.AccountNotFoundException.class);
    }
}
