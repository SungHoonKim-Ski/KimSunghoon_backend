package barley.wire.wirebarley.domain.idempotency;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import barley.wire.wirebarley.common.util.TimeUtil;

@Entity
@Table(name = "idempotency_records", indexes = {
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(nullable = false, length = 500)
    private String requestPath;

    @Column(length = 64)
    private String requestBodyHash;

    @Column(columnDefinition = "TEXT")
    private String responseBody;

    @Column(nullable = false)
    private Integer responseStatus;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public IdempotencyRecord(String idempotencyKey, String requestPath, String requestBodyHash,
            String responseBody, Integer responseStatus, LocalDateTime expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.requestPath = requestPath;
        this.requestBodyHash = requestBodyHash;
        this.responseBody = responseBody;
        this.responseStatus = responseStatus;
        this.createdAt = TimeUtil.nowDateTime();
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return TimeUtil.nowDateTime().isAfter(expiresAt);
    }
}
