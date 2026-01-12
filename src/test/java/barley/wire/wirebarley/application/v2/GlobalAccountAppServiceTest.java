package barley.wire.wirebarley.application.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import barley.wire.wirebarley.application.v2.GlobalAccountAppService;
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
import barley.wire.wirebarley.presentation.dto.request.CreateGlobalAccountRequest;
import barley.wire.wirebarley.presentation.dto.response.AccountResponse;
import barley.wire.wirebarley.presentation.dto.response.BalanceResponse;
import barley.wire.wirebarley.presentation.dto.response.TransactionListResponse;

import java.math.BigDecimal;
import java.util.Collections;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GlobalAccountAppServiceTest {

    @Mock
    private AccountService accountService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountValidator accountValidator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GlobalAccountAppService globalAccountAppService;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        // 테스트용 USD 계좌 초기화
        testAccount = new Account("110-111-111111", "Tester", Currency.USD);
        ReflectionTestUtils.setField(testAccount, "id", 1L);
    }

    @Test
    @DisplayName("글로벌 계좌 생성 성공")
    void createGlobalAccount_Success() {
        // [given]
        CreateGlobalAccountRequest request = new CreateGlobalAccountRequest("110-111-111111", "Tester", Currency.USD);
        when(accountService.save(any(Account.class))).thenReturn(testAccount);

        // [when]
        AccountResponse response = globalAccountAppService.createGlobalAccount(request);

        // [then]
        assertThat(response.accountNumber()).isEqualTo("110-111-111111");
        assertThat(response.currency()).isEqualTo(Currency.USD);
        verify(accountValidator).validateDuplicateAccountNumber(eq("110-111-111111"));
        verify(accountService, times(1)).save(any(Account.class));
    }

    @Test
    @DisplayName("글로벌 계좌 삭제 성공")
    void deleteGlobalAccount_Success() {
        // [given]
        when(accountService.getAccount(1L)).thenReturn(testAccount);

        // [when]
        globalAccountAppService.deleteGlobalAccount(1L);

        // [then]
        verify(accountService).delete(testAccount);
    }

    @Test
    @DisplayName("글로벌 계좌 입금 성공")
    void globalDeposit_Success() {
        // [given]
        BigDecimal amount = BigDecimal.valueOf(100.0);
        when(accountService.getAccountWithLock(1L)).thenReturn(testAccount);

        // [when]
        BalanceResponse response = globalAccountAppService.globalDeposit(1L, amount);

        // [then]
        // 글로벌 계좌 입금 후 통화 정보 및 이벤트 데이터 검증
        assertThat(response.balance()).isEqualByComparingTo(amount);
        assertThat(response.currency()).isEqualTo(Currency.USD);

        ArgumentCaptor<TransactionEvent> eventCaptor = ArgumentCaptor.forClass(TransactionEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TransactionEvent event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(event.amount()).isEqualByComparingTo(amount);
        assertThat(event.currency()).isEqualTo(Currency.USD);
    }

    @Test
    @DisplayName("잘못된 금액 입금 시도 시 실패")
    void globalDeposit_Fail_InvalidAmount() {
        // [given]
        BigDecimal amount = BigDecimal.valueOf(-100);
        doThrow(new InvalidAmountException("금액은 0보다 커야 합니다"))
                .when(accountValidator).validateAmount(amount);

        // [when & then]
        assertThatThrownBy(() -> globalAccountAppService.globalDeposit(1L, amount))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("글로벌 계좌 출금 성공")
    void withdraw_Success() {
        // [given]
        testAccount.deposit(BigDecimal.valueOf(500.0));
        BigDecimal amount = BigDecimal.valueOf(100.0);
        when(accountService.getAccountWithLock(1L)).thenReturn(testAccount);

        // [when]
        BalanceResponse response = globalAccountAppService.withdraw(1L, amount);

        // [then]
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.valueOf(400.0));
        assertThat(response.currency()).isEqualTo(Currency.USD);

        verify(accountValidator).checkWithdrawLimit(1L, amount);
        verify(accountValidator).validateBalance(eq(testAccount), eq(amount), anyString());
        verify(eventPublisher).publishEvent(any(TransactionEvent.class));
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
        assertThatThrownBy(() -> globalAccountAppService.withdraw(1L, amount))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    @DisplayName("글로벌 계좌 전액 출금 성공")
    void withdraw_FullBalance_Success() {
        // [given]
        BigDecimal balance = BigDecimal.valueOf(500.0);
        testAccount.deposit(balance);
        when(accountService.getAccountWithLock(1L)).thenReturn(testAccount);

        // [when]
        BalanceResponse response = globalAccountAppService.withdraw(1L, balance);

        // [then]
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(testAccount.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("거래 내역 조회 성공 (페이징 및 정렬 확인)")
    void getTransactions_Success() {
        // [given]
        Transaction tx1 = new Transaction(1L, TransactionType.DEPOSIT, BigDecimal.valueOf(100), BigDecimal.ZERO,
                BigDecimal.valueOf(100), null, Currency.USD);
        Transaction tx2 = new Transaction(1L, TransactionType.WITHDRAW, BigDecimal.valueOf(50), BigDecimal.ZERO,
                BigDecimal.valueOf(50), null, Currency.USD);

        PageRequest pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        // tx2가 더 최신 거래라고 가정하고 List.of(tx2, tx1) 반환
        when(transactionRepository.findByAccountIdOrderByCreatedAtDescIdDesc(eq(1L), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(tx2, tx1), pageable, 2));

        // [when]
        TransactionListResponse response = globalAccountAppService.getTransactions(1L, pageable);

        // [then]
        // 목록의 처음에 최신 거래(tx2)가 위치하는지 확인
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).type()).isEqualTo(TransactionType.WITHDRAW);
        assertThat(response.items().get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(response.items().get(1).type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.items().get(1).amount()).isEqualByComparingTo(BigDecimal.valueOf(100));

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
        verify(accountValidator).validateAccountExistence(1L);
    }

    @Test
    @DisplayName("존재하지 않는 계좌 거래내역 조회 시 실패")
    void getTransactions_Fail_AccountNotFound() {
        // [given]
        doThrow(new barley.wire.wirebarley.common.exception.AccountNotFoundException("계좌를 찾을 수 없음"))
                .when(accountValidator).validateAccountExistence(99L);

        // [when & then]
        assertThatThrownBy(() -> globalAccountAppService.getTransactions(99L, PageRequest.of(0, 10)))
                .isInstanceOf(barley.wire.wirebarley.common.exception.AccountNotFoundException.class);
    }

    @Test
    @DisplayName("거래 내역이 없을 때 빈 목록 반환")
    void getTransactions_Empty_Success() {
        // [given]
        PageRequest pageable = PageRequest.of(0, 10);
        when(transactionRepository.findByAccountIdOrderByCreatedAtDescIdDesc(eq(1L), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

        // [when]
        TransactionListResponse response = globalAccountAppService.getTransactions(1L, pageable);

        // [then]
        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }
}
