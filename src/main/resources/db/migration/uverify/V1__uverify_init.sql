drop table if exists bootstrap_datum;
drop table if exists user_credential;
drop table if exists fee_receiver;
drop table if exists state_datum;
drop table if exists uverify_certificate;

CREATE TABLE bootstrap_datum (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    authorization_token_script_hash VARCHAR(64) not null,
    token_name VARCHAR(255) not null,
    update_token_contract_credential VARCHAR(64) not null,
    fee INT not null,
    fee_interval INT not null,
    ttl BIGINT not null,
    creation_slot BIGINT not null,
    transaction_limit INT not null,
    transaction_id VARCHAR(64) not null,
    batch_size INT not null,
    invalidation_slot BIGINT
);

CREATE TABLE user_credential (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    credential VARCHAR(64) not null,
    bootstrap_datum_id BIGINT,
    FOREIGN KEY (bootstrap_datum_id) REFERENCES bootstrap_datum(id) ON DELETE CASCADE
);

CREATE TABLE fee_receiver (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    credential VARCHAR(64) not null,
    bootstrap_datum_id BIGINT,
    FOREIGN KEY (bootstrap_datum_id) REFERENCES bootstrap_datum(id) ON DELETE CASCADE
);

CREATE TABLE state_datum (
    id VARCHAR(64) PRIMARY KEY,
    owner VARCHAR(64) not null,
    creation_slot BIGINT not null,
    countdown INT not null,
    transaction_id VARCHAR(64) not null,
    bootstrap_datum_id BIGINT not null,
    invalidation_slot BIGINT,
    FOREIGN KEY (bootstrap_datum_id) REFERENCES bootstrap_datum(id)
);

CREATE TABLE state_datum_update (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot BIGINT not null,
    countdown INT not null,
    transaction_id VARCHAR(64) not null,
    state_datum_id VARCHAR(64),
    FOREIGN KEY (state_datum_id) REFERENCES state_datum(id)
);

CREATE TABLE uverify_certificate
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    hash                 varchar(128) not null,
    payment_credential   varchar(64) not null,
    block_hash           varchar(255) not null,
    block_number         bigint not null,
    transaction_id       varchar(255) not null,
    output_index         int not null,
    creation_time        timestamp not null,
    slot                 bigint not null,
    extra                varchar not null,
    hash_algorithm       varchar(100) not null,
    state_datum_id       varchar(64) not null,
    FOREIGN KEY (state_datum_id) REFERENCES state_datum(id)
);

CREATE INDEX idx_uverify_certificate_hash ON uverify_certificate(hash);
