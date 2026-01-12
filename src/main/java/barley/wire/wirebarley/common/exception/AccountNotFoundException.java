package barley.wire.wirebarley.common.exception;

public class AccountNotFoundException extends BaseException {
    private static final String ERROR_CODE = "ACCOUNT_NOT_FOUND";

    public AccountNotFoundException(String message) {
        super(ERROR_CODE, message);
    }
}
