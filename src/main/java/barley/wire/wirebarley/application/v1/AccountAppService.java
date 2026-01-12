package barley.wire.wirebarley.application.v1;

import barley.wire.wirebarley.domain.account.Account;
import barley.wire.wirebarley.domain.transaction.Transaction;
import barley.wire.wirebarley.domain.transaction.TransactionType;
import barley.wire.wirebarley.common.event.TransactionEvent;
import barley.wire.wirebarley.infrastructure.repository.TransactionRepository;
import barley.wire.wirebarley.presentation.dto.request.CreateAccountRequest;
import barley.wire.wirebarley.presentation.dto.response.AccountResponse;
import barley.wire.wirebarley.presentation.dto.response.BalanceResponse;
import barley.wire.wirebarley.presentation.dto.response.TransactionListResponse;
import barley.wire.wirebarley.presentation.dto.response.TransactionResponse;
import barley.wire.wirebarley.infrastructure.service.AccountService;
import barley.wire.wirebarley.common.validator.AccountValidator;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountAppService {

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final AccountValidator accountValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        accountValidator.validateDuplicateAccountNumber(request.accountNumber());

        Account savedAccount = accountService.save(CreateAccountRequest.toEntity(request));

        return AccountResponse.from(savedAccount);
    }

    @Transactional
    public void deleteAccount(Long accountId) {
        Account account = accountService.getAccount(accountId);

        accountService.delete(account);
    }

    @Transactional
    public BalanceResponse deposit(Long accountId, BigDecimal amount) {
        accountValidator.validateAmount(amount);
        Account account = accountService.getAccountWithLock(accountId);

        account.deposit(amount);
        publishTransactionEvent(account, TransactionType.DEPOSIT, amount);

        return BalanceResponse.from(account);
    }

    @Transactional
    public BalanceResponse withdraw(Long accountId, BigDecimal amount) {
        accountValidator.validateAmount(amount);
        Account account = accountService.getAccountWithLock(accountId);

        accountValidator.checkWithdrawLimit(account, amount);
        accountValidator.validateBalance(account, amount, "잔액이 부족합니다");

        account.withdraw(amount);
        publishTransactionEvent(account, TransactionType.WITHDRAW, amount);

        return BalanceResponse.from(account);
    }

    public BalanceResponse getBalance(Long accountId) {
        Account account = accountService.getAccount(accountId);

        return BalanceResponse.from(account);
    }

    public AccountResponse getAccount(Long accountId) {
        Account account = accountService.getAccount(accountId);

        return AccountResponse.from(account);
    }

    public TransactionListResponse getTransactions(Long accountId, Pageable pageable) {
        accountValidator.validateAccountExistence(accountId);

        Page<Transaction> transactionPage = transactionRepository.findByAccountIdOrderByCreatedAtDescIdDesc(accountId,
                pageable);

        Page<TransactionResponse> dtoPage = transactionPage.map(TransactionResponse::from);

        return TransactionListResponse.from(dtoPage);
    }

    private void publishTransactionEvent(Account account, TransactionType type, BigDecimal amount) {
        TransactionEvent event = TransactionEvent.builder()
                .accountId(account.getId())
                .type(type)
                .amount(amount)
                .fee(BigDecimal.ZERO)
                .currency(account.getCurrency())
                .balanceSnapshot(account.getBalance())
                .build();

        eventPublisher.publishEvent(event);
    }
}
