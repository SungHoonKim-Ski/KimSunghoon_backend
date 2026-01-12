package barley.wire.wirebarley.common.validator;

import static barley.wire.wirebarley.common.constants.TransferConstants.DAILY_TRANSFER_LIMIT;
import static barley.wire.wirebarley.common.constants.TransferConstants.DAILY_WITHDRAW_LIMIT;

import barley.wire.wirebarley.domain.account.Account;
import barley.wire.wirebarley.domain.transaction.TransactionType;
import barley.wire.wirebarley.common.exception.InvalidAmountException;
import barley.wire.wirebarley.common.exception.LimitExceededException;
import barley.wire.wirebarley.common.exception.InsufficientBalanceException;
import barley.wire.wirebarley.common.exception.DuplicateAccountException;
import barley.wire.wirebarley.common.util.TimeUtil;
import barley.wire.wirebarley.infrastructure.repository.TransactionRepository;
import barley.wire.wirebarley.infrastructure.service.AccountService;
import barley.wire.wirebarley.infrastructure.service.ExchangeRateService;
import barley.wire.wirebarley.domain.account.Currency;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountValidator {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final ExchangeRateService exchangeRateService;

    public void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("금액은 0보다 커야 합니다");
        }
    }

    public void checkWithdrawLimit(Account account, BigDecimal amount) {

        // 오늘 이미 출금한 금액 합산 (해당 계좌의 통화 기준)
        BigDecimal dailyWithdrawn = transactionRepository.sumAmountByAccountIdAndTypeAndCreatedAtAfter(account.getId(),
                TransactionType.WITHDRAW, TimeUtil.atStartOfDay());

        // 합계 및 현재 요청 금액을 KRW로 환산하여 한도 체크
        BigDecimal totalWithdrawnInAccountCurrency = dailyWithdrawn.add(amount);
        BigDecimal totalWithdrawnInKRW = totalWithdrawnInAccountCurrency;

        if (account.getCurrency() != Currency.KRW) {
            totalWithdrawnInKRW = exchangeRateService.convertAmount(totalWithdrawnInAccountCurrency,
                    account.getCurrency(), Currency.KRW);
        }

        if (totalWithdrawnInKRW.compareTo(DAILY_WITHDRAW_LIMIT) > 0) {
            throw new LimitExceededException("일일 출금 한도를 초과했습니다 (한도: 100만원)");
        }
    }

    public void checkTransferLimit(Long accountId, BigDecimal amount) {
        Account account = accountService.getAccount(accountId);
        BigDecimal dailyTransferred = transactionRepository.sumAmountByAccountIdAndTypeAndCreatedAtAfter(accountId,
                TransactionType.TRANSFER_OUT, TimeUtil.atStartOfDay());

        BigDecimal totalInAccountCurrency = dailyTransferred.add(amount);
        BigDecimal totalInKRW = totalInAccountCurrency;

        if (account.getCurrency() != Currency.KRW) {
            totalInKRW = exchangeRateService.convertAmount(totalInAccountCurrency,
                    account.getCurrency(), Currency.KRW);
        }

        if (totalInKRW.compareTo(DAILY_TRANSFER_LIMIT) > 0) {
            throw new LimitExceededException("일일 이체 한도를 초과했습니다 (한도: 300만원)");
        }
    }

    public void checkGlobalTransferLimit(Long accountId, BigDecimal amount) {
        Account account = accountService.getAccount(accountId);
        BigDecimal dailyTransferred = transactionRepository.sumAmountByAccountIdAndTypeAndCreatedAtAfter(accountId,
                TransactionType.TRANSFER_OUT, TimeUtil.atStartOfDay());

        BigDecimal totalInAccountCurrency = dailyTransferred.add(amount);
        BigDecimal totalInKRW = totalInAccountCurrency;

        if (account.getCurrency() != Currency.KRW) {
            totalInKRW = exchangeRateService.convertAmount(totalInAccountCurrency,
                    account.getCurrency(), Currency.KRW);
        }

        if (totalInKRW.compareTo(DAILY_TRANSFER_LIMIT) > 0) {
            throw new LimitExceededException("일일 해외 송금 한도를 초과했습니다 (한도: 300만원)");
        }
    }

    public void validateTransfer(Long fromAccountId, Long toAccountId) {
        if (fromAccountId.equals(toAccountId)) {
            throw new InvalidAmountException("동일한 계좌로 이체할 수 없습니다");
        }
    }

    public void validateBalance(Account account, BigDecimal totalDeduction, String message) {
        if (account.getBalance().compareTo(totalDeduction) < 0) {
            throw new InsufficientBalanceException(message != null ? message : "잔액이 부족합니다");
        }
    }

    public void validateDuplicateAccountNumber(String accountNumber) {
        if (accountService.existsByAccountNumber(accountNumber)) {
            throw new DuplicateAccountException("이미 존재하는 계좌번호입니다: " + accountNumber);
        }
    }

    public void validateAccountExistence(Long accountId) {
        accountService.getAccount(accountId);
    }

    public void validateSameCurrency(Account fromAccount, Account toAccount) {
        if (fromAccount.getCurrency() != toAccount.getCurrency()) {
            throw new InvalidAmountException(
                    String.format("통화가 다른 계좌 간 이체는 불가능합니다. (출금: %s, 입금: %s). v2/GlobalTransfer API를 사용하세요.",
                            fromAccount.getCurrency(), toAccount.getCurrency()));
        }
    }
}
