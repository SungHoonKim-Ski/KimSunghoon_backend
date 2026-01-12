package barley.wire.wirebarley.infrastructure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.infrastructure.client.ExchangeRateClient;
import barley.wire.wirebarley.infrastructure.client.ExchangeRateFallbackClient;
import barley.wire.wirebarley.infrastructure.repository.ExchangeRateRepository;
import barley.wire.wirebarley.presentation.dto.response.ExchangeRateApiResponse;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = { ExchangeRateService.class })
@Import(ExchangeRateCachingTest.CachingConfig.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
class ExchangeRateCachingTest {

    @Autowired
    private ExchangeRateService exchangeRateService;

    @MockitoBean
    private ExchangeRateRepository exchangeRateRepository;

    @MockitoBean
    private ExchangeRateClient exchangeRateClient;

    @MockitoBean
    private ExchangeRateFallbackClient exchangeRateFallbackClient;

    @Autowired
    private CacheManager cacheManager;

    @TestConfiguration
    @EnableCaching
    static class CachingConfig {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("exchangeRates");
        }
    }

    @Test
    @DisplayName("동일한 통화 쌍에 대해 반복 호출 시 캐시가 적용되어 원본 메서드 호출이 제한되는지 검증")
    void verifyExchangeRateCaching() {
        // [given]
        Currency from = Currency.KRW;
        Currency to = Currency.USD;
        BigDecimal expectedRate = new BigDecimal("0.0007");

        // DB 캐시는 비어있고 API 호출로 환율을 가져오는 시나리오
        when(exchangeRateRepository.findLatestRate(from, to)).thenReturn(Optional.empty());
        when(exchangeRateClient.getExchangeRates(from.name()))
                .thenReturn(new ExchangeRateApiResponse(from.name(), Map.of(to.name(), expectedRate)));

        // [when] 1차 호출 (Cache Miss)
        BigDecimal rate1 = exchangeRateService.getExchangeRate(from, to);
        assertThat(rate1).isEqualByComparingTo(expectedRate);

        // [when] 2차 호출 (Cache Hit)
        BigDecimal rate2 = exchangeRateService.getExchangeRate(from, to);
        assertThat(rate2).isEqualByComparingTo(expectedRate);

        // [then] 서비스 로직(DB 조회/API 호출)이 1번만 실행되었는지 검증
        verify(exchangeRateRepository, times(1)).findLatestRate(from, to);
        verify(exchangeRateClient, times(1)).getExchangeRates(from.name());

        // 캐시 매니저에 데이터가 있는지 확인
        assertThat(cacheManager.getCache("exchangeRates").get("KRW_USD")).isNotNull();
    }

    @Test
    @DisplayName("서로 다른 통화 쌍은 각각 별도로 캐싱되는지 검증")
    void verifySeparateCachingForDifferentCurrencies() {
        // [given]
        Currency from = Currency.KRW;
        Currency to1 = Currency.USD;
        Currency to2 = Currency.JPY;

        when(exchangeRateRepository.findLatestRate(any(), any())).thenReturn(Optional.empty());
        when(exchangeRateClient.getExchangeRates(from.name()))
                .thenReturn(new ExchangeRateApiResponse(from.name(),
                        Map.of(to1.name(), new BigDecimal("0.0007"), to2.name(), new BigDecimal("0.1"))));

        // [when]
        exchangeRateService.getExchangeRate(from, to1);
        exchangeRateService.getExchangeRate(from, to2);

        // [then] 각각 호출되었는지 확인
        verify(exchangeRateRepository, times(1)).findLatestRate(from, to1);
        verify(exchangeRateRepository, times(1)).findLatestRate(from, to2);

        assertThat(cacheManager.getCache("exchangeRates").get("KRW_USD")).isNotNull();
        assertThat(cacheManager.getCache("exchangeRates").get("KRW_JPY")).isNotNull();
    }
}
