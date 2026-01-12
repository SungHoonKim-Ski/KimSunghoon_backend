package barley.wire.wirebarley.infrastructure.repository;

import barley.wire.wirebarley.domain.idempotency.IdempotencyRecord;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}
