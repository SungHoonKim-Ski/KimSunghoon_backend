package barley.wire.wirebarley.repository;

import org.springframework.stereotype.Repository;

@Repository
public class HealthRepository {

    public Integer getHealthStatus() {
        return 1;
    }
}
