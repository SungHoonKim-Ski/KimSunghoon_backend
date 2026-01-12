package barley.wire.wirebarley.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "exchangeRateClient", url = "https://api.exchangerate-api.com/v4/latest")
public interface ExchangeRateClient extends ExchangeRateApi {
}
