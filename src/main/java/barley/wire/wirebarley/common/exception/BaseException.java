package barley.wire.wirebarley.common.exception;

/**
 * 모든 커스텀 예외의 기본 클래스
 * 에러 코드를 내장하여 GlobalExceptionHandler에서 일관되게 처리
 */
public abstract class BaseException extends RuntimeException {
    private final String errorCode;

    protected BaseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
