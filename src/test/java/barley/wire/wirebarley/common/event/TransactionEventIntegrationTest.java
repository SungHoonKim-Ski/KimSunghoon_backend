package barley.wire.wirebarley.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import barley.wire.wirebarley.IntegrationTestBase;
import barley.wire.wirebarley.application.v1.AccountAppService;
import barley.wire.wirebarley.application.v1.TransferAppService;
import barley.wire.wirebarley.domain.transaction.Transaction;
import barley.wire.wirebarley.domain.transaction.TransactionType;
import barley.wire.wirebarley.presentation.dto.request.CreateAccountRequest;
import barley.wire.wirebarley.presentation.dto.request.TransferRequest;
import barley.wire.wirebarley.presentation.dto.response.AccountResponse;
import barley.wire.wirebarley.infrastructure.repository.TransactionRepository;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

public class TransactionEventIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AccountAppService accountAppService;

    @Autowired
    private TransferAppService transferAppService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("입금 및 출금 시 이벤트 발행을 통해 거래내역이 DB에 저장되는지 검증")
    void verifyTransactionEventPersistence_DepositAndWithdraw() {
        // 1. Account Creation (No transaction event for creation based on current
        // logic)
        CreateAccountRequest createRequest = new CreateAccountRequest("EVENT-TEST-001", "EventTester");
        AccountResponse account = accountAppService.createAccount(createRequest);

        // Verify no transactions yet
        List<Transaction> transactionsInitial = transactionRepository
                .findByAccountIdOrderByCreatedAtDescIdDesc(account.id(), PageRequest.of(0, 100))
                .getContent();
        assertThat(transactionsInitial).isEmpty();

        // 2. Deposit
        BigDecimal depositAmount = BigDecimal.valueOf(10000);
        accountAppService.deposit(account.id(), depositAmount);

        // Verify Deposit Transaction
        List<Transaction> transactionsAfterDeposit = transactionRepository
                .findByAccountIdOrderByCreatedAtDescIdDesc(account.id(), PageRequest.of(0, 100))
                .getContent();
        assertThat(transactionsAfterDeposit).hasSize(1);
        Transaction depositTx = transactionsAfterDeposit.get(0);
        assertThat(depositTx.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(depositTx.getAmount()).isEqualByComparingTo(depositAmount);
        assertThat(depositTx.getBalanceSnapshot()).isEqualByComparingTo(depositAmount);

        // 3. Withdraw
        BigDecimal withdrawAmount = BigDecimal.valueOf(4000);
        accountAppService.withdraw(account.id(), withdrawAmount);

        // Verify Withdraw Transaction
        List<Transaction> transactionsAfterWithdraw = transactionRepository
                .findByAccountIdOrderByCreatedAtDescIdDesc(account.id(), PageRequest.of(0, 100))
                .getContent();
        assertThat(transactionsAfterWithdraw).hasSize(2);

        Transaction withdrawTx = transactionsAfterWithdraw.stream()
                .filter(t -> t.getType() == TransactionType.WITHDRAW)
                .findFirst()
                .orElseThrow();

        assertThat(withdrawTx.getAmount()).isEqualByComparingTo(withdrawAmount);
        assertThat(withdrawTx.getBalanceSnapshot()).isEqualByComparingTo(BigDecimal.valueOf(6000));
    }

    @Test
    @DisplayName("이체 시 이벤트 발행을 통해 송금/입금 거래내역이 DB에 저장되는지 검증")
    void verifyTransactionEventPersistence_Transfer() {
        // Setup Accounts
        AccountResponse fromAccount = accountAppService
                .createAccount(new CreateAccountRequest("EVENT-SEND-001", "Sender"));
        AccountResponse toAccount = accountAppService
                .createAccount(new CreateAccountRequest("EVENT-RECV-001", "Receiver"));

        accountAppService.deposit(fromAccount.id(), BigDecimal.valueOf(100000));

        transactionRepository.deleteAll();

        BigDecimal transferAmount = BigDecimal.valueOf(50000);
        TransferRequest transferRequest = new TransferRequest(fromAccount.id(), toAccount.id(), transferAmount);
        transferAppService.transfer(transferRequest);

        List<Transaction> fromTransactions = transactionRepository
                .findByAccountIdOrderByCreatedAtDescIdDesc(fromAccount.id(), PageRequest.of(0, 100))
                .getContent();
        assertThat(fromTransactions).hasSize(1);
        Transaction senderTx = fromTransactions.get(0);
        assertThat(senderTx.getType()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(senderTx.getAmount()).isEqualByComparingTo(transferAmount);

        assertThat(senderTx.getFee()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(senderTx.getBalanceSnapshot()).isEqualByComparingTo(BigDecimal.valueOf(49500));

        List<Transaction> toTransactions = transactionRepository
                .findByAccountIdOrderByCreatedAtDescIdDesc(toAccount.id(), PageRequest.of(0, 100))
                .getContent();

        assertThat(toTransactions).hasSize(1);
        Transaction receiverTx = toTransactions.get(0);
        assertThat(receiverTx.getType()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(receiverTx.getAmount()).isEqualByComparingTo(transferAmount);
        assertThat(receiverTx.getBalanceSnapshot()).isEqualByComparingTo(transferAmount);
    }
}
