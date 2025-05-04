DROP TABLE IF EXISTS connected_good CASCADE;
DROP TABLE IF EXISTS connected_good_update CASCADE;
DROP TABLE IF EXISTS social_hub CASCADE;

CREATE TABLE connected_good (
    id VARCHAR(64) PRIMARY KEY,
    creation_slot BIGINT NOT NULL
);

CREATE TABLE connected_good_update (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(64) NOT NULL,
    output_index INT NOT NULL,
    slot BIGINT NOT NULL,
    connected_good_id VARCHAR(64),
    FOREIGN KEY (connected_good_id) REFERENCES connected_good(id)
);

CREATE TABLE social_hub (
    id BIGSERIAL PRIMARY KEY,
    password VARCHAR(64) NOT NULL,
    owner VARCHAR(64),
    picture VARCHAR(32),
    name VARCHAR(160),
    subtitle VARCHAR(160),
    x VARCHAR(160),
    telegram VARCHAR(160),
    instagram VARCHAR(160),
    discord VARCHAR(160),
    reddit VARCHAR(160),
    youtube VARCHAR(160),
    linkedin VARCHAR(160),
    github VARCHAR(160),
    website VARCHAR(160),
    adahandle VARCHAR(160),
    email VARCHAR(160),
    asset_id VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(64) NOT NULL,
    output_index INT NOT NULL,
    creation_slot BIGINT NOT NULL,
    batch_id VARCHAR(64),
    FOREIGN KEY (batch_id) REFERENCES connected_good(id)
);