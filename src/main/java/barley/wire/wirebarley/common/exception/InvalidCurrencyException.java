package barley.wire.wirebarley.common.exception;

public class InvalidCurrencyException extends BaseException {
    private static final String ERROR_CODE = "INVALID_CURRENCY";

    public InvalidCurrencyException(String message) {
        super(ERROR_CODE, message);
    }

    public static InvalidCurrencyException mismatch(String accountCurrency, String requestCurrency) {
        return new InvalidCurrencyException(
                String.format("계좌 통화(%s)와 요청 통화(%s)가 일치하지 않습니다", accountCurrency, requestCurrency));
    }
}
