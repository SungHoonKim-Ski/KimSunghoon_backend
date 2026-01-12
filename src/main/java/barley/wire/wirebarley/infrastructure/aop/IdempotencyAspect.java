package barley.wire.wirebarley.infrastructure.aop;

import barley.wire.wirebarley.common.event.IdempotencyRecordedEvent;
import barley.wire.wirebarley.common.exception.DuplicateIdempotencyKeyException;
import barley.wire.wirebarley.common.exception.MissingIdempotencyKeyException;
import barley.wire.wirebarley.common.util.HashUtil;
import barley.wire.wirebarley.domain.idempotency.IdempotencyRecord;
import barley.wire.wirebarley.infrastructure.service.IdempotencyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 멱등성 보장을 위한 AOP 컴포넌트
 * Idempotency-Key 헤더를 사용하여 중복 요청을 방지합니다.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class IdempotencyAspect {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = currentRequest()
                .orElseThrow(() -> new IllegalStateException("멱등성 처리를 위한 HTTP 요청 컨텍스트를 찾을 수 없습니다."));

        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (key == null || key.isBlank()) {
            if (idempotent.required()) {
                throw new MissingIdempotencyKeyException("Idempotency-Key 헤더가 필요합니다.");
            }
            return joinPoint.proceed();
        }

        String path = request.getRequestURI();
        String requestHash = requestHash(joinPoint.getArgs()).orElse(null);

        // 1) 동일 키에 대한 기존 기록이 있으면 캐시된 응답 반환
        Optional<IdempotencyRecord> recordOpt = idempotencyService.findByKey(key);
        if (recordOpt.isPresent()) {
            IdempotencyRecord record = recordOpt.get();

            if (isHashMismatch(record, requestHash)) {
                log.warn("멱등성 키 중복 사용 감지 - 서로 다른 요청 본문. key={}, path={}, 기존 해시={}, 신규 해시={}",
                        key, path, record.getRequestBodyHash(), requestHash);
                throw new DuplicateIdempotencyKeyException("이미 사용된 멱등성 키로 서로 다른 요청이 전달되었습니다.");
            }

            log.debug("멱등성 요청 재처리 - 캐시된 응답 반환. key={}, path={}", key, path);

            // 메서드 리턴 타입 확인
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Class<?> returnType = signature.getReturnType();

            if (returnType.isAssignableFrom(ResponseEntity.class)) {
                // ResponseEntity인 경우 처리
                return ResponseEntity.status(record.getResponseStatus())
                        .body(objectMapper.readTree(record.getResponseBody()));
            } else {
                // 일반 DTO인 경우 처리
                return objectMapper.readValue(record.getResponseBody(), returnType);
            }
        }

        // 2) 실제 메서드 실행
        Object result = joinPoint.proceed();

        // 3) 결과 기록을 위해 멱등성 이벤트 발행
        publishRecordEvent(key, path, requestHash, result);

        return result;
    }

    /**
     * 현재 요청의 HttpServletRequest를 가져옵니다.
     */
    private Optional<HttpServletRequest> currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return Optional.ofNullable(attributes).map(ServletRequestAttributes::getRequest);
    }

    private Optional<String> requestHash(Object[] args) {
        try {
            if (args == null || args.length == 0) {
                return Optional.empty();
            }
            // 일반적으로 첫 번째 인자가 @RequestBody DTO이므로 이를 해싱 대상으로 삼음
            String json = objectMapper.writeValueAsString(args[0]);
            String hash = HashUtil.calculateSHA256(json);
            log.debug("IdempotencyAspect: Generated request hash: {} from content: {}", hash, json);
            return Optional.of(hash);
        } catch (JsonProcessingException e) {
            log.warn("요청 본문 해시 생성에 실패했습니다.", e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("요청 본문 해시 생성 중 예기치 않은 오류가 발생했습니다.", e);
            return Optional.empty();
        }
    }

    private boolean isHashMismatch(IdempotencyRecord record, String newHash) {
        String oldHash = record.getRequestBodyHash();
        if (oldHash == null || newHash == null) {
            return false;
        }
        return !oldHash.equals(newHash);
    }

    private void publishRecordEvent(
            String key,
            String path,
            String requestHash,
            Object result) {
        String responseBody;
        int status = HttpStatus.OK.value();

        if (result instanceof ResponseEntity<?> responseEntity) {
            responseBody = idempotencyService.serializeResponse(responseEntity.getBody());
            status = responseEntity.getStatusCode().value();
        } else {
            responseBody = idempotencyService.serializeResponse(result);
        }

        eventPublisher.publishEvent(
                new IdempotencyRecordedEvent(
                        key,
                        path,
                        requestHash,
                        responseBody,
                        status));
    }
}