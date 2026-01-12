package barley.wire.wirebarley.infrastructure.listener;

import barley.wire.wirebarley.common.event.IdempotencyRecordedEvent;
import barley.wire.wirebarley.infrastructure.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 멱등성 레코드 저장 이벤트 리스너
 * AFTER_COMMIT을 사용하여 메인 트랜잭션 커밋 후 처리
 * 저장 실패 시에도 메인 로직에 영향을 주지 않음
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyEventListener {

    private final IdempotencyService idempotencyService;

    /**
     * 멱등성 레코드 저장 이벤트 처리
     * AFTER_COMMIT: 메인 트랜잭션 커밋 후 실행하여 완전히 분리
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleIdempotencyRecorded(IdempotencyRecordedEvent event) {
        try {
            idempotencyService.saveRecord(
                event.idempotencyKey(),
                event.requestPath(),
                event.requestBodyHash(),
                event.responseBody(),
                event.responseStatus());

            log.info("멱등성 레코드 저장 성공: key={}", event.idempotencyKey());
        } catch (Exception e) {
            log.error("멱등성 레코드 저장 실패 (메인 로직은 성공): key={}", event.idempotencyKey(), e);
        }
    }
}
