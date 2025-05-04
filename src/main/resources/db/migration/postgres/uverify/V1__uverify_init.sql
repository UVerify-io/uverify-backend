DROP TABLE IF EXISTS bootstrap_datum CASCADE;
DROP TABLE IF EXISTS user_credential CASCADE;
DROP TABLE IF EXISTS fee_receiver CASCADE;
DROP TABLE IF EXISTS state_datum CASCADE;
DROP TABLE IF EXISTS state_datum_update CASCADE;
DROP TABLE IF EXISTS uverify_certificate CASCADE;

CREATE TABLE bootstrap_datum (
    id BIGSERIAL PRIMARY KEY,
    authorization_token_script_hash VARCHAR(64) NOT NULL,
    token_name VARCHAR(255) NOT NULL,
    update_token_contract_credential VARCHAR(64) NOT NULL,
    fee INT NOT NULL,
    fee_interval INT NOT NULL,
    ttl BIGINT NOT NULL,
    creation_slot BIGINT NOT NULL,
    transaction_limit INT NOT NULL,
    transaction_id VARCHAR(64) NOT NULL,
    batch_size INT NOT NULL,
    invalidation_slot BIGINT
);

CREATE TABLE user_credential (
    id BIGSERIAL PRIMARY KEY,
    credential VARCHAR(64) NOT NULL,
    bootstrap_datum_id BIGINT,
    FOREIGN KEY (bootstrap_datum_id) REFERENCES bootstrap_datum(id) ON DELETE CASCADE
);

CREATE TABLE fee_receiver (
    id BIGSERIAL PRIMARY KEY,
    credential VARCHAR(64) NOT NULL,
    bootstrap_datum_id BIGINT,
    FOREIGN KEY (bootstrap_datum_id) REFERENCES bootstrap_datum(id) ON DELETE CASCADE
);

CREATE TABLE state_datum (
    id VARCHAR(64) PRIMARY KEY,
    owner VARCHAR(64) NOT NULL,
    creation_slot BIGINT NOT NULL,
    countdown INT NOT NULL,
    transaction_id VARCHAR(64) NOT NULL,
    bootstrap_datum_id BIGINT NOT NULL,
    invalidation_slot BIGINT,
    FOREIGN KEY (bootstrap_datum_id) REFERENCES bootstrap_datum(id)
);

CREATE TABLE state_datum_update (
    id BIGSERIAL PRIMARY KEY,
    slot BIGINT NOT NULL,
    countdown INT NOT NULL,
    transaction_id VARCHAR(64) NOT NULL,
    state_datum_id VARCHAR(64),
    FOREIGN KEY (state_datum_id) REFERENCES state_datum(id)
);

CREATE TABLE uverify_certificate (
    id BIGSERIAL PRIMARY KEY,
    hash VARCHAR(128) NOT NULL,
    payment_credential VARCHAR(64) NOT NULL,
    block_hash VARCHAR(255) NOT NULL,
    block_number BIGINT NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    output_index INT NOT NULL,
    creation_time TIMESTAMP NOT NULL,
    slot BIGINT NOT NULL,
    extra VARCHAR NOT NULL,
    hash_algorithm VARCHAR(100) NOT NULL,
    state_datum_id VARCHAR(64) NOT NULL,
    FOREIGN KEY (state_datum_id) REFERENCES state_datum(id)
);

CREATE INDEX idx_uverify_certificate_hash ON uverify_certificate(hash);