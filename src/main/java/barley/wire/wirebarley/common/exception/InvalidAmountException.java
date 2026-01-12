package barley.wire.wirebarley.common.exception;

public class InvalidAmountException extends BaseException {
    private static final String ERROR_CODE = "INVALID_AMOUNT";

    public InvalidAmountException(String message) {
        super(ERROR_CODE, message);
    }
}
