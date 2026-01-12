package barley.wire.wirebarley.common.exception;

public class LimitExceededException extends BaseException {
    private static final String ERROR_CODE = "LIMIT_EXCEEDED";

    public LimitExceededException(String message) {
        super(ERROR_CODE, message);
    }
}
