package barley.wire.wirebarley.common.exception;

public class MissingIdempotencyKeyException extends BaseException {
    private static final String ERROR_CODE = "Missing_Idempotency_Key";

    public MissingIdempotencyKeyException(String message) {
        super(ERROR_CODE, message);
    }
}
