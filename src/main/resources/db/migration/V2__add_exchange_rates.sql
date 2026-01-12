-- Add exchange_rates table for global transfers

CREATE TABLE exchange_rates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_currency VARCHAR(10) NOT NULL,
    to_currency VARCHAR(10) NOT NULL,
    rate DECIMAL(20, 6) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_currency_updated (from_currency, to_currency, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert initial exchange rates (updated 2026-01-11)
INSERT INTO exchange_rates (from_currency, to_currency, rate) VALUES
('KRW', 'USD', 0.000686),
('KRW', 'JPY', 0.108158),
('KRW', 'EUR', 0.000589),
('USD', 'KRW', 1457.72),
('JPY', 'KRW', 9.246),
('EUR', 'KRW', 1697.79);
