package barley.wire.wirebarley.common.exception;

import barley.wire.wirebarley.presentation.dto.response.ErrorResponse;
import barley.wire.wirebarley.common.util.TimeUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;

import java.time.LocalDateTime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 모든 커스텀 비즈니스 예외 처리 BaseException을 상속한 예외는 모두 여기서 처리됨
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex) {
        log.error("Business exception occurred: {}", ex.getMessage());

        HttpStatus status;
        if (ex instanceof AccountNotFoundException) {
            status = HttpStatus.NOT_FOUND;
        } else if (ex instanceof InvalidAmountException || ex instanceof MissingIdempotencyKeyException) {
            status = HttpStatus.BAD_REQUEST;
        } else if (ex instanceof InsufficientBalanceException || ex instanceof DuplicateAccountException
                || ex instanceof DuplicateIdempotencyKeyException) {
            status = HttpStatus.CONFLICT;
        } else if (ex instanceof LimitExceededException) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        ErrorResponse error = new ErrorResponse(ex.getErrorCode(), ex.getMessage(), TimeUtil.nowDateTime());
        return ResponseEntity.status(status).body(error);
    }

    /**
     * 유효성 검증 실패 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        StringBuilder message = new StringBuilder();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            message.append(error.getDefaultMessage()).append("; ");
        }

        ErrorResponse error = new ErrorResponse("INVALID_REQUEST", message.toString(), TimeUtil.nowDateTime());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * 낙관적 락 충돌 처리
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockException ex) {
        log.warn("낙관적 락 예외 발생", ex);
        ErrorResponse error = new ErrorResponse("CONCURRENT_MODIFICATION", "동시에 수정되었습니다. 다시 시도해주세요",
                LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * 비관적 락 충돌 처리
     */
    @ExceptionHandler(PessimisticLockException.class)
    public ResponseEntity<ErrorResponse> handlePessimisticLock(PessimisticLockException ex) {
        log.warn("비관적 락 예외 발생", ex);
        ErrorResponse error = new ErrorResponse("RESOURCE_LOCKED", "다른 요청을 처리 중입니다. 잠시 후 다시 시도해주세요",
                LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * 데이터 무결성 위반 처리
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("데이터 무결성 위반", ex);
        ErrorResponse error = new ErrorResponse("DATA_INTEGRITY_VIOLATION", "데이터 무결성 제약 위반이 발생했습니다",
                LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler({ ServletRequestBindingException.class })
    public ResponseEntity<ErrorResponse> handleBindingException(Exception ex) {
        log.error("Request binding exception: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse("BAD_REQUEST", ex.getMessage(), TimeUtil.nowDateTime());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<ErrorResponse> handleJsonProcessingException(JsonProcessingException ex) {
        log.error("요청 직렬화 실패", ex);
        ErrorResponse error = new ErrorResponse("JSON_PARSE_ERROR", "요청 데이터를 처리할 수 없습니다",
                LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * 예상치 못한 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("예상치 못한 오류 발생", ex);
        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR", "예상치 못한 오류가 발생했습니다", TimeUtil.nowDateTime());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
