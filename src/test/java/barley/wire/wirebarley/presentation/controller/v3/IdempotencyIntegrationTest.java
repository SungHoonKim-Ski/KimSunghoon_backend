package barley.wire.wirebarley.presentation.controller.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import barley.wire.wirebarley.IntegrationTestBase;
import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.infrastructure.repository.IdempotencyRecordRepository;
import barley.wire.wirebarley.presentation.dto.request.GlobalTransferRequest;
import barley.wire.wirebarley.presentation.dto.response.AccountResponse;
import barley.wire.wirebarley.presentation.dto.response.ExchangeRateApiResponse;
import barley.wire.wirebarley.presentation.dto.response.GlobalTransferResponse;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdempotencyIntegrationTest extends IntegrationTestBase {

    @Autowired
    private IdempotencyRecordRepository recordRepository;

    @Test
    @DisplayName("멱등성 헤더를 포함한 송금 요청이 성공하고, 재요청 시 캐시된 응답을 반환한다")
    void idempotentGlobalTransfer_Success_And_Replay() throws Exception {
        // [given]
        AccountResponse from = fixture.createGlobalAccount("110-IDEM-001", "Sender", Currency.KRW);
        fixture.deposit(from.id(), BigDecimal.valueOf(1000000));
        AccountResponse to = fixture.createGlobalAccount("220-IDEM-002", "Receiver", Currency.USD);

        ExchangeRateApiResponse mockExchange = new ExchangeRateApiResponse("KRW",
                Map.of("USD", new BigDecimal("0.0007")));
        when(exchangeRateClient.getExchangeRates("KRW")).thenReturn(mockExchange);

        GlobalTransferRequest request = new GlobalTransferRequest(from.id(), to.id(),
                BigDecimal.valueOf(100000));
        String idempotencyKey = UUID.randomUUID().toString();

        // [when] 1차 요청
        MvcResult result1 = mockMvc.perform(post("/api/v3/global-transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andReturn();

        assertThat(result1.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        GlobalTransferResponse response1 = objectMapper.readValue(result1.getResponse().getContentAsString(),
                GlobalTransferResponse.class);

        // [then] DB에 기록되었는지 확인 (Async이므로 약간의 대기가 필요할 수 있음)
        // 여기서는 Not_Supported 이므로 실제 DB에 들어감.
        Thread.sleep(500); // Async 저장을 위한 대기
        assertThat(recordRepository.findByIdempotencyKey(idempotencyKey)).isPresent();

        // [when] 2차 요청 (동일 키, 동일 바디)
        MvcResult result2 = mockMvc.perform(post("/api/v3/global-transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        // [then] 캐시된 응답 반환 확인
        assertThat(result2.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        GlobalTransferResponse response2 = objectMapper.readValue(result2.getResponse().getContentAsString(),
                GlobalTransferResponse.class);
        assertThat(response2).isEqualTo(response1);

        // 잔액이 한 번만 차감되었는지 확인 (1,000,000 - (100,000 + 1,000 fee) = 899,000)
        assertThat(response1.fromBalance()).isEqualByComparingTo(new BigDecimal("899000"));
        assertThat(response2.fromBalance()).isEqualByComparingTo(new BigDecimal("899000"));
    }

    @Test
    @DisplayName("멱등성 키가 동일하지만 바디가 다를 경우 409 Conflict를 반환한다")
    void idempotentGlobalTransfer_HashMismatch_Conflict() throws Exception {
        // [given]
        AccountResponse from = fixture.createGlobalAccount("110-IDEM-003", "Sender", Currency.KRW);
        fixture.deposit(from.id(), BigDecimal.valueOf(500000));
        AccountResponse to = fixture.createGlobalAccount("220-IDEM-004", "Receiver", Currency.USD);

        when(exchangeRateClient.getExchangeRates("KRW"))
                .thenReturn(new ExchangeRateApiResponse("KRW",
                        Map.of("USD", new BigDecimal("0.0007"))));

        String idempotencyKey = UUID.randomUUID().toString();
        GlobalTransferRequest request1 = new GlobalTransferRequest(from.id(), to.id(),
                BigDecimal.valueOf(100000));
        GlobalTransferRequest request2 = new GlobalTransferRequest(from.id(), to.id(),
                BigDecimal.valueOf(200000));

        // 1차 요청 성공
        mockMvc.perform(post("/api/v3/global-transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andReturn();

        Thread.sleep(500);

        // [when] 2차 요청 (동일 키, 다른 바디)
        MvcResult result2 = mockMvc.perform(post("/api/v3/global-transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andReturn();

        // [then]
        assertThat(result2.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(result2.getResponse().getErrorMessage() == null ? result2.getResponse().getContentAsString()
                : result2.getResponse().getErrorMessage())
                .contains("이미 사용된 멱등성 키");
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 누락된 경우 400 Bad Request를 반환한다")
    void idempotentGlobalTransfer_MissingHeader_BadRequest() throws Exception {
        // [given]
        GlobalTransferRequest request = new GlobalTransferRequest(1L, 2L, BigDecimal.valueOf(10000));

        // [when]
        MvcResult result = mockMvc.perform(post("/api/v3/global-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        // [then]
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }
}
