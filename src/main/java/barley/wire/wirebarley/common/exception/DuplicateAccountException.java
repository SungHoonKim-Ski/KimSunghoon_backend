package barley.wire.wirebarley.common.exception;

public class DuplicateAccountException extends BaseException {
    private static final String ERROR_CODE = "ALREADY_EXISTS";

    public DuplicateAccountException(String message) {
        super(ERROR_CODE, message);
    }
}
