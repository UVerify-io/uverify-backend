-- AYM Vision Extension tables

CREATE TABLE aym_user_profile (
    public_key      VARCHAR(64)  NOT NULL,
    profile_id      VARCHAR(255) NOT NULL,
    salt            VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (public_key, profile_id)
);

CREATE TABLE aym_user_content (
    public_key  VARCHAR(64)  NOT NULL,
    profile_id  VARCHAR(255) NOT NULL,
    content_id  VARCHAR(255) NOT NULL,
    source      VARCHAR(50)  NOT NULL,
    granted_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (public_key, profile_id, content_id),
    FOREIGN KEY (public_key, profile_id) REFERENCES aym_user_profile(public_key, profile_id) ON DELETE CASCADE
);

CREATE TABLE aym_user_course_state (
    public_key   VARCHAR(64)  NOT NULL,
    profile_id   VARCHAR(255) NOT NULL,
    course_id    VARCHAR(255) NOT NULL,
    status       VARCHAR(50)  NOT NULL,
    reported_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (public_key, profile_id, course_id),
    FOREIGN KEY (public_key, profile_id) REFERENCES aym_user_profile(public_key, profile_id) ON DELETE CASCADE
);

CREATE TABLE aym_voucher (
    id          UUID         PRIMARY KEY,
    content_id  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE aym_redeemed_voucher (
    id                    UUID         PRIMARY KEY,
    content_id            VARCHAR(255) NOT NULL,
    redeemed_by_public_key VARCHAR(64) NOT NULL,
    redeemed_by_profile_id VARCHAR(255) NOT NULL,
    redeemed_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (redeemed_by_public_key, redeemed_by_profile_id)
        REFERENCES aym_user_profile(public_key, profile_id) ON DELETE CASCADE
);

CREATE TABLE aym_stripe_purchase (
    session_id   VARCHAR(255) PRIMARY KEY,
    public_key   VARCHAR(64)  NOT NULL,
    profile_id   VARCHAR(255) NOT NULL,
    content_id   VARCHAR(255) NOT NULL,
    verified_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (public_key, profile_id)
        REFERENCES aym_user_profile(public_key, profile_id) ON DELETE CASCADE
);

CREATE TABLE aym_mpf_anchor (
    tree_version    BIGINT       PRIMARY KEY,
    root            VARCHAR(128) NOT NULL,
    uverify_tx_hash VARCHAR(128),
    anchored_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_aym_content_profile ON aym_user_content(public_key, profile_id);
CREATE INDEX idx_aym_course_profile   ON aym_user_course_state(public_key, profile_id);
CREATE INDEX idx_aym_redeemed_by      ON aym_redeemed_voucher(redeemed_by_public_key, redeemed_by_profile_id);
CREATE INDEX idx_aym_voucher_content  ON aym_voucher(content_id);
