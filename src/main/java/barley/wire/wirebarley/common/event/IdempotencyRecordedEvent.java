package barley.wire.wirebarley.common.event;

/**
 * 멱등성 레코드 저장 이벤트
 * 메인 트랜잭션 커밋 후 비동기로 처리하여 저장 실패가 메인 로직에 영향을 주지 않도록 함
 */
public record IdempotencyRecordedEvent(
        String idempotencyKey,
        String requestPath,
        String requestBodyHash,
        String responseBody,
        int responseStatus) {
}
