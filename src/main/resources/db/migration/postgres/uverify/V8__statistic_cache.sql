CREATE TABLE uverify_statistic (
    stat_key   VARCHAR(64) PRIMARY KEY,
    stat_value BIGINT      NOT NULL,
    updated_at TIMESTAMP   NOT NULL
);

CREATE INDEX idx_uverify_certificate_transaction_id
    ON uverify_certificate (transaction_id);
