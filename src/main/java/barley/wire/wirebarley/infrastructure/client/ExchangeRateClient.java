package barley.wire.wirebarley.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.annotation.Order;

@Order(1)
@FeignClient(name = "exchangeRateClient", url = "https://api.exchangerate-api.com/v4/latest")
public interface ExchangeRateClient extends ExchangeRateApi {
}
