package barley.wire.wirebarley.common.exception;

public class InsufficientBalanceException extends BaseException {
    private static final String ERROR_CODE = "INSUFFICIENT_BALANCE";

    public InsufficientBalanceException(String message) {
        super(ERROR_CODE, message);
    }
}
