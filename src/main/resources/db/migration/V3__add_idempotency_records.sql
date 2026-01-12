-- 멱등성 레코드 테이블 추가

CREATE TABLE idempotency_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    request_path VARCHAR(255) NOT NULL,
    request_body_hash VARCHAR(64),
    response_body LONGTEXT,
    response_status INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    INDEX idx_idempotency_key (idempotency_key),
    INDEX idx_idempotency_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
