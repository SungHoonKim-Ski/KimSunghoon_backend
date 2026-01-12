package barley.wire.wirebarley.common.event;

import barley.wire.wirebarley.domain.transaction.Transaction;
import barley.wire.wirebarley.infrastructure.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventListener {

    private final TransactionRepository transactionRepository;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void handleTransactionEvent(TransactionEvent event) {
        log.info("Transaction 이벤트 저장됨 - Account: {}, Type: {}, Amount: {}", event.accountId(), event.type(),
                event.amount());

        Transaction transaction = new Transaction(event.accountId(), event.type(), event.amount(), event.fee(),
                event.balanceSnapshot(), event.relatedAccountId(), event.currency());

        transactionRepository.save(transaction);
    }
}
