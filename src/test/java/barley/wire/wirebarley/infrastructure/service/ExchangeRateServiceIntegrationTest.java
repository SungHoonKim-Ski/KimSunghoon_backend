package barley.wire.wirebarley.infrastructure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import barley.wire.wirebarley.IntegrationTestBase;
import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.domain.exchange.ExchangeRate;
import barley.wire.wirebarley.infrastructure.service.ExchangeRateService;
import barley.wire.wirebarley.presentation.dto.response.ExchangeRateApiResponse;
import barley.wire.wirebarley.infrastructure.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

class ExchangeRateServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // 모든 테스트 전에 환율 캐시를 초기화합니다.
        Objects.requireNonNull(cacheManager.getCache("exchangeRates")).clear();
        // DB 초기화
        exchangeRateRepository.deleteAll();
    }

    @Test
    @DisplayName("Primary API 실패 시 Fallback API에서 환율을 가져오는지 테스트")
    void fallbackToSecondaryApi() {
        // given
        Currency from = Currency.KRW;
        Currency to = Currency.USD;
        BigDecimal fallbackRate = BigDecimal.valueOf(0.0007);

        // Primary API 실패 설정
        when(exchangeRateClient.getExchangeRates(from.name())).thenThrow(new RuntimeException("Primary API Failure"));

        // Fallback API 성공 설정
        when(exchangeRateFallbackClient.getExchangeRates(from.name()))
                .thenReturn(new ExchangeRateApiResponse(from.name(), Map.of(to.name(), fallbackRate)));

        // when
        BigDecimal result = exchangeRateService.getExchangeRate(from, to);

        // then
        assertThat(result).isEqualByComparingTo(fallbackRate);

        // DB에 저장되었는지 확인
        assertThat(exchangeRateRepository.findLatestRate(from, to)).isPresent()
                .get()
                .extracting(ExchangeRate::getRate)
                .matches(rate -> rate.compareTo(fallbackRate) == 0);
    }

    @Test
    @DisplayName("모든 API 실패 시 DB에 저장된 최신 환율을 사용하는지 테스트")
    void fallbackToDb() {
        // given
        Currency from = Currency.KRW;
        Currency to = Currency.USD;
        BigDecimal dbRate = BigDecimal.valueOf(0.0008);

        // DB에 미리 데이터 저장 (API 실패 시 폴백으로 쓰기 위함)
        exchangeRateRepository.save(new ExchangeRate(from, to, dbRate));
        Objects.requireNonNull(cacheManager.getCache("exchangeRates")).clear();

        // 모든 API 실패 유도
        when(exchangeRateClient.getExchangeRates(from.name())).thenThrow(new RuntimeException("Primary API Failure"));
        when(exchangeRateFallbackClient.getExchangeRates(from.name()))
                .thenThrow(new RuntimeException("Fallback API Failure"));

        // when
        BigDecimal result = exchangeRateService.getExchangeRate(from, to);

        // then
        assertThat(result).isEqualByComparingTo(dbRate);

        // 이 로직은 먼저 DB를 확인하므로 API 호출이 아예 발생하지 않아야 함
        verify(exchangeRateClient, never()).getExchangeRates(anyString());
        verify(exchangeRateFallbackClient, never()).getExchangeRates(anyString());
    }

    @Test
    @DisplayName("모든 API 실패하고 DB도 비어있을 때 기본값 1.0을 반환하는지 테스트")
    void ultimateFallbackToOne() {
        // given
        Currency from = Currency.KRW;
        Currency to = Currency.USD;

        // 모든 API 실패 설정
        when(exchangeRateClient.getExchangeRates(from.name())).thenThrow(new RuntimeException("Primary Failure"));
        when(exchangeRateFallbackClient.getExchangeRates(from.name()))
                .thenThrow(new RuntimeException("Fallback Failure"));

        // when
        BigDecimal result = exchangeRateService.getExchangeRate(from, to);

        // then
        assertThat(result).isEqualByComparingTo(BigDecimal.ONE);
    }
}
