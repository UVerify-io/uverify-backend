CREATE TABLE short_link (
    short_code VARCHAR(10) PRIMARY KEY,
    certificate_hash VARCHAR(100) NOT NULL,
    click_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_short_link_certificate_hash ON short_link (certificate_hash);
