package barley.wire.wirebarley.infrastructure.service;

import barley.wire.wirebarley.infrastructure.client.ExchangeRateApi;
import barley.wire.wirebarley.infrastructure.client.ExchangeRateClient;
import barley.wire.wirebarley.infrastructure.client.ExchangeRateFallbackClient;
import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.domain.exchange.ExchangeRate;
import barley.wire.wirebarley.presentation.dto.response.ExchangeRateApiResponse;
import barley.wire.wirebarley.infrastructure.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateClient exchangeRateClient;
    private final ExchangeRateFallbackClient exchangeRateFallbackClient;

    /** 환율 조회 (캐시 적용 - 10분) 캐시 미스 시 외부 API 호출 후 DB 저장 */
    @Cacheable(value = "exchangeRates", key = "#fromCurrency + '_' + #toCurrency")
    @Transactional
    public BigDecimal getExchangeRate(Currency fromCurrency, Currency toCurrency) {
        log.info("Fetching exchange rate from {} to {}", fromCurrency, toCurrency);

        // 같은 통화면 1.0 반환
        if (fromCurrency == toCurrency) {
            return BigDecimal.ONE;
        }

        // 1. DB에서 최신 환율 조회
        return exchangeRateRepository.findLatestRate(fromCurrency, toCurrency)
                .map(ExchangeRate::getRate)
                .orElseGet(() -> fetchAndSaveExchangeRate(fromCurrency, toCurrency));
    }

    /**
     * 외부 API에서 환율 조회 후 DB 저장 폴백 전략: Primary API → Secondary API → DB Cache → 1.0
     */
    private BigDecimal fetchAndSaveExchangeRate(Currency fromCurrency, Currency toCurrency) {
        // 1차 시도: Primary API
        try {
            log.info("Calling primary API for exchange rate: {} -> {}", fromCurrency, toCurrency);
            BigDecimal rate = fetchFromApi(exchangeRateClient, fromCurrency, toCurrency);
            if (rate != null) {
                saveExchangeRate(fromCurrency, toCurrency, rate);
                return rate;
            }
        } catch (Exception e) {
            log.warn("Primary API failed: {}", e.getMessage());
        }

        // 2차 시도: Fallback API
        try {
            log.info("Calling fallback API for exchange rate: {} -> {}", fromCurrency, toCurrency);
            BigDecimal rate = fetchFromApi(exchangeRateFallbackClient, fromCurrency, toCurrency);
            if (rate != null) {
                saveExchangeRate(fromCurrency, toCurrency, rate);
                return rate;
            }
        } catch (Exception e) {
            log.warn("Fallback API failed: {}", e.getMessage());
        }

        log.warn("All external APIs failed, using DB cache");
        return exchangeRateRepository.findLatestRate(fromCurrency, toCurrency)
                .map(ExchangeRate::getRate)
                .orElse(BigDecimal.ONE); // 4차: 최후의 수단
    }

    private BigDecimal fetchFromApi(ExchangeRateApi client, Currency fromCurrency, Currency toCurrency) {
        ExchangeRateApiResponse response = client.getExchangeRates(fromCurrency.name());

        if (response == null || response.rates() == null) {
            return null;
        }

        return response.rates().get(toCurrency.name());
    }

    private void saveExchangeRate(Currency fromCurrency, Currency toCurrency, BigDecimal rate) {
        ExchangeRate exchangeRate = new ExchangeRate(fromCurrency, toCurrency, rate);

        exchangeRateRepository.save(exchangeRate);
    }

    /** 금액 환전 계산 */
    public BigDecimal convertAmount(BigDecimal amount, Currency fromCurrency, Currency toCurrency) {
        BigDecimal rate = getExchangeRate(fromCurrency, toCurrency);

        return amount.multiply(rate);
    }
}
