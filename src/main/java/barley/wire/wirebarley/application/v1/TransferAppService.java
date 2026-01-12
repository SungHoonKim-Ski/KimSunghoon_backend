package barley.wire.wirebarley.application.v1;

import static barley.wire.wirebarley.common.constants.TransferConstants.TRANSFER_FEE_RATE;

import barley.wire.wirebarley.common.util.MoneyUtils;
import barley.wire.wirebarley.domain.account.Account;
import barley.wire.wirebarley.domain.transaction.TransactionType;
import barley.wire.wirebarley.common.event.TransactionEvent;
import barley.wire.wirebarley.infrastructure.service.AccountService;
import org.springframework.data.util.Pair;
import barley.wire.wirebarley.presentation.dto.request.TransferRequest;
import barley.wire.wirebarley.presentation.dto.response.TransferResponse;
import barley.wire.wirebarley.common.validator.AccountValidator;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferAppService {

    private final AccountService accountService;
    private final AccountValidator accountValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        BigDecimal amount = request.amount();
        accountValidator.validateAmount(amount);
        accountValidator.validateTransfer(request.fromAccountId(), request.toAccountId());

        // 데드락 방지를 위해 일관된 순서로 락 획득
        Pair<Account, Account> accountPair = accountService.getAccountsWithLockOrdered(request.fromAccountId(),
                request.toAccountId());

        Account fromAccount = accountPair.getFirst();
        Account toAccount = accountPair.getSecond();

        accountValidator.validateSameCurrency(fromAccount, toAccount);
        accountValidator.checkTransferLimit(fromAccount.getId(), amount);

        BigDecimal fee = MoneyUtils.multiply(amount, TRANSFER_FEE_RATE, fromAccount.getCurrency());
        BigDecimal totalDeduction = MoneyUtils.add(amount, fee, fromAccount.getCurrency());

        accountValidator.validateBalance(fromAccount, totalDeduction, "이체를 위한 잔액이 부족합니다");

        fromAccount.withdraw(totalDeduction);
        toAccount.deposit(amount);

        // 거래 이벤트 발행
        publishTransferEvents(fromAccount, toAccount, amount, fee);

        return TransferResponse.of(fromAccount, toAccount, amount, fee);
    }

    private void publishTransferEvents(Account fromAccount, Account toAccount, BigDecimal amount, BigDecimal fee) {
        TransactionEvent outEvent = TransactionEvent.builder()
                .accountId(fromAccount.getId())
                .type(TransactionType.TRANSFER_OUT)
                .amount(amount)
                .fee(fee)
                .currency(fromAccount.getCurrency())
                .balanceSnapshot(fromAccount.getBalance())
                .relatedAccountId(toAccount.getId())
                .build();

        TransactionEvent inEvent = TransactionEvent.builder()
                .accountId(toAccount.getId())
                .type(TransactionType.TRANSFER_IN)
                .amount(amount)
                .fee(BigDecimal.ZERO)
                .currency(toAccount.getCurrency())
                .balanceSnapshot(toAccount.getBalance())
                .relatedAccountId(fromAccount.getId())
                .build();

        eventPublisher.publishEvent(outEvent);
        eventPublisher.publishEvent(inEvent);
    }
}
