package barley.wire.wirebarley.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.core.Ordered;

@Configuration
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 10)
public class TransactionConfig {
}
