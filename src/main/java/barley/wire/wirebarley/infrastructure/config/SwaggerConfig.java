package barley.wire.wirebarley.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI wireBarleyOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("송금 서비스 (WireBarley) API 명세서")
                        .description("계좌 관리, 입출금 및 해외 송금을 위한 API 문서입니다.")
                        .version("v1.0.0"));
    }
}
