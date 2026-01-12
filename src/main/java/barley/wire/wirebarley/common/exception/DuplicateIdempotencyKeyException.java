package barley.wire.wirebarley.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 중복된 멱등성 키 사용 시 발생하는 예외 (바디 해시 불일치)
 */
public class DuplicateIdempotencyKeyException extends BaseException {
    public DuplicateIdempotencyKeyException(String message) {
        super("DUPLICATE_IDEMPOTENCY_KEY", message);
    }

    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
