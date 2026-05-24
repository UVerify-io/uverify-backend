CREATE TABLE uverify_credential (
    id                 BIGSERIAL PRIMARY KEY,
    auth_cert_hash     VARCHAR(100) NOT NULL UNIQUE,
    payment_credential VARCHAR(100) NOT NULL,
    credential_type    VARCHAR(100) NOT NULL,
    keri_aid           VARCHAR(200),
    keri_schema        VARCHAR(200),
    keri_oobi          VARCHAR(500),
    keri_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    revoked            BOOLEAN NOT NULL DEFAULT FALSE,
    tx_hash            VARCHAR(100),
    slot               BIGINT NOT NULL,
    last_verified_at   TIMESTAMP,
    acdc_attributes    TEXT
);

CREATE INDEX idx_uverify_credential_lookup
    ON uverify_credential (payment_credential, credential_type, revoked);
