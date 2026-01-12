package barley.wire.wirebarley.infrastructure.client;

import barley.wire.wirebarley.presentation.dto.response.ExchangeRateApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public interface ExchangeRateApi {

    @GetMapping("/{baseCurrency}")
    ExchangeRateApiResponse getExchangeRates(@PathVariable("baseCurrency") String baseCurrency);
}
