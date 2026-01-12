package barley.wire.wirebarley.infrastructure.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "barley.wire.wirebarley.infrastructure.client")
public class FeignConfig {
}
