CREATE TABLE aym_completion_certificate (
    public_key  VARCHAR(64)   NOT NULL,
    profile_id  VARCHAR(255)  NOT NULL,
    cert_hash   VARCHAR(64)   NOT NULL,
    verify_url  VARCHAR(2000) NOT NULL,
    issued_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (public_key, profile_id)
);
