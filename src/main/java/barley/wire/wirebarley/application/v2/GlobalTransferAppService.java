package barley.wire.wirebarley.application.v2;

import static barley.wire.wirebarley.common.constants.TransferConstants.EXCHANGE_FEE_RATE;
import static barley.wire.wirebarley.common.constants.TransferConstants.TRANSFER_FEE_RATE;

import barley.wire.wirebarley.common.event.TransactionEvent;
import barley.wire.wirebarley.common.util.MoneyUtils;
import barley.wire.wirebarley.common.validator.AccountValidator;
import barley.wire.wirebarley.domain.account.Account;
import barley.wire.wirebarley.domain.transaction.TransactionType;
import barley.wire.wirebarley.infrastructure.aop.Idempotent;
import barley.wire.wirebarley.infrastructure.service.AccountService;
import barley.wire.wirebarley.infrastructure.service.ExchangeRateService;
import barley.wire.wirebarley.presentation.dto.request.GlobalTransferRequest;
import barley.wire.wirebarley.presentation.dto.response.GlobalTransferResponse;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GlobalTransferAppService {

    private final AccountService accountService;
    private final ExchangeRateService exchangeRateService;
    private final AccountValidator accountValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Idempotent(required = false)
    @Transactional
    @Operation(summary = "글로벌 송금 (자동 환전)", description = "다른 통화 계좌 간 자동 환전 송금을 실행합니다. (수수료: 이체 1% + 다른 통화 환전 0.5%, 한도(KRW기준) : 이체한도 100만원, 입금한도 300만원 적용)")
    public GlobalTransferResponse globalTransfer(GlobalTransferRequest request) {
        BigDecimal amount = request.amount();
        // 1. 요청 검증
        accountValidator.validateAmount(amount);
        accountValidator.validateTransfer(request.fromAccountId(), request.toAccountId());

        // 2. 계좌 조회 및 락 획득 (데드락 방지)
        Pair<Account, Account> accountPair = accountService.getAccountsWithLockOrdered(request.fromAccountId(),
                request.toAccountId());

        Account fromAccount = accountPair.getFirst();
        Account toAccount = accountPair.getSecond();

        // 3. 환율 정보 조회 및 환전 금액 계산
        BigDecimal exchangeRate = exchangeRateService.getExchangeRate(fromAccount.getCurrency(),
                toAccount.getCurrency());
        BigDecimal convertedAmount = calculateConvertedAmount(amount, fromAccount, toAccount);

        // 4. 환전 수수료 계산 및 최종 입금 금액 산출
        BigDecimal exchangeFee = calculateExchangeFee(fromAccount, toAccount, convertedAmount);
        BigDecimal finalConvertedAmount = MoneyUtils.subtract(convertedAmount, exchangeFee,
                toAccount.getCurrency());

        // 5. 이체 수수료 계산 (출금 통화 기준)
        BigDecimal transferFee = MoneyUtils.multiply(amount, TRANSFER_FEE_RATE, fromAccount.getCurrency());
        BigDecimal totalAmountToWithdraw = MoneyUtils.add(amount, transferFee, fromAccount.getCurrency());

        // 6. 실행 가능 여부 검증 (잔액, 한도)
        validateExecution(fromAccount, amount, totalAmountToWithdraw);

        // 7. 계좌 잔액 업데이트 (출금/입금)
        executeTransfer(fromAccount, toAccount, totalAmountToWithdraw, finalConvertedAmount);

        // 8. 거래 이벤트 발행
        publishTransferEvents(fromAccount, toAccount, amount, finalConvertedAmount, transferFee);

        return GlobalTransferResponse.builder()
                .fromAccountId(fromAccount.getId())
                .toAccountId(toAccount.getId())
                .originalAmount(amount)
                .fromCurrency(fromAccount.getCurrency())
                .convertedAmount(finalConvertedAmount)
                .toCurrency(toAccount.getCurrency())
                .exchangeRate(exchangeRate)
                .fee(transferFee)
                .fromBalance(fromAccount.getBalance())
                .toBalance(toAccount.getBalance())
                .build();
    }

    // 환전 된 금액을 계산하고 소수점을 정리합니다.
    private BigDecimal calculateConvertedAmount(BigDecimal amount, Account fromAccount, Account toAccount) {
        BigDecimal convertedAmount = exchangeRateService.convertAmount(amount, fromAccount.getCurrency(),
                toAccount.getCurrency());
        return MoneyUtils.scale(convertedAmount, toAccount.getCurrency());
    }

    // 환전 수수료를 계산합니다. 동일 통화일 경우 수수료는 0원입니다.
    private BigDecimal calculateExchangeFee(Account fromAccount, Account toAccount, BigDecimal convertedAmount) {
        if (fromAccount.getCurrency() == toAccount.getCurrency()) {
            return BigDecimal.ZERO;
        }
        return MoneyUtils.multiply(convertedAmount, EXCHANGE_FEE_RATE, toAccount.getCurrency());
    }

    // 한도와 잔액을 검증합니다.
    private void validateExecution(Account fromAccount, BigDecimal amount, BigDecimal totalAmountToWithdraw) {
        // 잔액 확인 (출금 통화 기준)
        accountValidator.validateBalance(fromAccount, totalAmountToWithdraw, "수수료를 포함한 잔액이 부족합니다");

        // 출금 계좌 한도 확인 (이체 한도 + 출금 한도) - KRW 기준
        accountValidator.checkGlobalTransferLimit(fromAccount.getId(), amount);
        accountValidator.checkWithdrawLimit(fromAccount, totalAmountToWithdraw);
    }

    // 실제 계좌의 잔액 변경
    private void executeTransfer(Account fromAccount, Account toAccount, BigDecimal withdrawAmount,
            BigDecimal depositAmount) {
        fromAccount.withdraw(withdrawAmount);
        toAccount.deposit(depositAmount);
    }

    // 트랜잭션 이벤트 발행
    private void publishTransferEvents(Account fromAccount, Account toAccount, BigDecimal amount,
            BigDecimal finalConvertedAmount, BigDecimal transferFee) {
        TransactionEvent outEvent = TransactionEvent.builder()
                .accountId(fromAccount.getId())
                .type(TransactionType.TRANSFER_OUT)
                .amount(amount)
                .fee(transferFee)
                .currency(fromAccount.getCurrency())
                .balanceSnapshot(fromAccount.getBalance())
                .relatedAccountId(toAccount.getId())
                .build();

        TransactionEvent inEvent = TransactionEvent.builder()
                .accountId(toAccount.getId())
                .type(TransactionType.TRANSFER_IN)
                .amount(finalConvertedAmount)
                .fee(BigDecimal.ZERO)
                .currency(toAccount.getCurrency())
                .balanceSnapshot(toAccount.getBalance())
                .relatedAccountId(fromAccount.getId())
                .build();

        eventPublisher.publishEvent(outEvent);
        eventPublisher.publishEvent(inEvent);
    }
}
