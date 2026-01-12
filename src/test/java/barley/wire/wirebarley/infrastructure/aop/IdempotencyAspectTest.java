package barley.wire.wirebarley.infrastructure.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import barley.wire.wirebarley.common.event.IdempotencyRecordedEvent;
import barley.wire.wirebarley.common.exception.DuplicateIdempotencyKeyException;
import barley.wire.wirebarley.common.exception.MissingIdempotencyKeyException;
import barley.wire.wirebarley.common.util.HashUtil;
import barley.wire.wirebarley.domain.idempotency.IdempotencyRecord;
import barley.wire.wirebarley.infrastructure.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;
import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class IdempotencyAspectTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private HttpServletRequest request;

    @Mock
    private Idempotent idempotent;

    @InjectMocks
    private IdempotencyAspect idempotencyAspect;

    private String testKey;
    private String testPath = "/api/test";

    @BeforeEach
    void setUp() {
        testKey = UUID.randomUUID().toString();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("최초 요청 시 메서드를 실행하고 결과를 반환하며 이벤트를 발행한다")
    void handleIdempotency_FirstRequest() throws Throwable {
        // [given]
        when(request.getHeader("Idempotency-Key")).thenReturn(testKey);
        when(request.getRequestURI()).thenReturn(testPath);
        when(idempotencyService.findByKey(testKey)).thenReturn(Optional.empty());

        Object[] args = new Object[] { "arg1" };
        when(joinPoint.getArgs()).thenReturn(args);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"data\":\"test\"}");

        String responseDto = "success_dto";
        when(joinPoint.proceed()).thenReturn(responseDto);
        when(idempotencyService.serializeResponse(responseDto)).thenReturn("\"success_dto\"");

        // [when]
        Object result = idempotencyAspect.handleIdempotency(joinPoint, idempotent);

        // [then]
        assertThat(result).isEqualTo(responseDto);
        verify(joinPoint).proceed();
        verify(eventPublisher).publishEvent(any(IdempotencyRecordedEvent.class));
    }

    @Test
    @DisplayName("중복 요청 시 캐시된 응답을 반환하고 메서드를 실행하지 않는다")
    void handleIdempotency_DuplicateRequest() throws Throwable {
        // [given]
        when(request.getHeader("Idempotency-Key")).thenReturn(testKey);
        when(request.getRequestURI()).thenReturn(testPath);

        String cachedBody = "{\"result\":\"cached\"}";
        IdempotencyRecord record = mock(IdempotencyRecord.class);
        when(record.getResponseBody()).thenReturn(cachedBody);

        when(idempotencyService.findByKey(testKey)).thenReturn(Optional.of(record));

        Object[] args = new Object[] { "arg1" };
        when(joinPoint.getArgs()).thenReturn(args);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"data\":\"test\"}");

        String expectedHash = HashUtil.calculateSHA256("{\"data\":\"test\"}");
        when(record.getRequestBodyHash()).thenReturn(expectedHash);

        // 결과 타입이 ResponseEntity가 아닌 일반 DTO인 경우
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        doReturn(String.class).when(methodSignature).getReturnType();
        String expectedDto = "replayed_dto";
        when(objectMapper.readValue(cachedBody, String.class)).thenReturn(expectedDto);

        // [when]
        Object result = idempotencyAspect.handleIdempotency(joinPoint, idempotent);

        // [then]
        assertThat(result).isEqualTo(expectedDto);
        verify(joinPoint, never()).proceed();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("멱등성 키가 동일하지만 바디가 다르면 DuplicateIdempotencyKeyException을 발생시킨다")
    void handleIdempotency_HashMismatch_Conflict() throws Throwable {
        // [given]
        when(request.getHeader("Idempotency-Key")).thenReturn(testKey);
        when(request.getRequestURI()).thenReturn(testPath);

        IdempotencyRecord record = mock(IdempotencyRecord.class);
        when(record.getRequestBodyHash()).thenReturn("different_hash");

        when(idempotencyService.findByKey(testKey)).thenReturn(Optional.of(record));

        Object[] args = new Object[] { "arg2" };
        when(joinPoint.getArgs()).thenReturn(args);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"data\":\"different\"}");

        // [when & then]
        assertThatThrownBy(() -> idempotencyAspect.handleIdempotency(joinPoint, idempotent))
                .isInstanceOf(DuplicateIdempotencyKeyException.class)
                .hasMessageContaining("이미 사용된 멱등성 키");

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 예외를 발생시킨다")
    void handleIdempotency_MissingKey() {
        // [given]
        when(request.getHeader("Idempotency-Key")).thenReturn(null);
        when(idempotent.required()).thenReturn(true);

        // [when & then]
        assertThatThrownBy(() -> idempotencyAspect.handleIdempotency(joinPoint, idempotent))
                .isInstanceOf(MissingIdempotencyKeyException.class);
    }
}
