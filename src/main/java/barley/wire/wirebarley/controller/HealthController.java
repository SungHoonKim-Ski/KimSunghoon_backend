package barley.wire.wirebarley.controller;

import barley.wire.wirebarley.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/health")
    public ResponseEntity<Integer> health() {
        Integer status = healthService.checkHealth();
        return ResponseEntity.ok(status);
    }

}
