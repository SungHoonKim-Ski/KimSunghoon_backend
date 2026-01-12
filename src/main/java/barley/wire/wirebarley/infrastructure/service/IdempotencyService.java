package barley.wire.wirebarley.infrastructure.service;

import static barley.wire.wirebarley.common.constants.IdempotencyConstants.DEFAULT_TTL;

import barley.wire.wirebarley.domain.idempotency.IdempotencyRecord;
import barley.wire.wirebarley.infrastructure.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return idempotencyRecordRepository.findByIdempotencyKey(key).filter(record -> !record.isExpired());
    }

    @Transactional
    public void saveRecord(String key, String path, String requestHash, String responseBody, int status) {
        LocalDateTime expiresAt = LocalDateTime.now().plus(DEFAULT_TTL);

        IdempotencyRecord idempotencyRecord = new IdempotencyRecord(key, path, requestHash, responseBody, status,
                expiresAt);

        idempotencyRecordRepository.save(idempotencyRecord);
        log.info("Saved idempotency record for key: {}", key);
    }

    public String serializeResponse(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize response", e);
            return null;
        }
    }
}
