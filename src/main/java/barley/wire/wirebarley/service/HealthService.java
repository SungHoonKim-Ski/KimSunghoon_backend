package barley.wire.wirebarley.service;

import barley.wire.wirebarley.repository.HealthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final HealthRepository healthRepository;

    public Integer checkHealth() {
        return healthRepository.getHealthStatus();
    }
}
