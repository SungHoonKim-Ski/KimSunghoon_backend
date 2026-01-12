package barley.wire.wirebarley.application.v2;

import barley.wire.wirebarley.domain.account.Account;
import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.domain.transaction.Transaction;
import barley.wire.wirebarley.domain.transaction.TransactionType;
import barley.wire.wirebarley.common.event.TransactionEvent;
import barley.wire.wirebarley.infrastructure.repository.TransactionRepository;
import barley.wire.wirebarley.presentation.dto.request.CreateGlobalAccountRequest;
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
public class GlobalAccountAppService {

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final AccountValidator accountValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AccountResponse createGlobalAccount(CreateGlobalAccountRequest request) {
        accountValidator.validateDuplicateAccountNumber(request.accountNumber());

        Account savedAccount = accountService.save(CreateGlobalAccountRequest.toEntity(request));

        return AccountResponse.from(savedAccount);
    }

    @Transactional
    public void deleteGlobalAccount(Long accountId) {
        Account account = accountService.getAccount(accountId);

        accountService.delete(account);
    }

    @Transactional
    public BalanceResponse globalDeposit(Long accountId, BigDecimal amount) {
        accountValidator.validateAmount(amount);
        Account account = accountService.getAccountWithLock(accountId);

        account.deposit(amount);
        publishTransactionEvent(account, TransactionType.DEPOSIT, amount, account.getCurrency());

        return BalanceResponse.from(account);
    }

    @Transactional
    public BalanceResponse withdraw(Long accountId, BigDecimal amount) {
        accountValidator.validateAmount(amount);
        Account account = accountService.getAccountWithLock(accountId);

        accountValidator.checkWithdrawLimit(accountId, amount);
        accountValidator.validateBalance(account, amount, "잔액이 부족합니다");

        account.withdraw(amount);
        publishTransactionEvent(account, TransactionType.WITHDRAW, amount, account.getCurrency());

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

        return new TransactionListResponse(dtoPage.getContent(), dtoPage.getNumber(), dtoPage.getSize(),
                dtoPage.getTotalElements(), dtoPage.getTotalPages());
    }

    private void publishTransactionEvent(Account account, TransactionType type, BigDecimal amount, Currency currency) {
        TransactionEvent event = TransactionEvent.builder()
                .accountId(account.getId())
                .type(type)
                .amount(amount)
                .fee(BigDecimal.ZERO)
                .currency(currency)
                .balanceSnapshot(account.getBalance())
                .build();

        eventPublisher.publishEvent(event);
    }
}
