package barley.wire.wirebarley.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "exchangeRateFallbackClient", url = "https://open.er-api.com/v6/latest")
public interface ExchangeRateFallbackClient extends ExchangeRateApi {
}
