drop table if exists connected_good;
drop table if exists connected_good_update;
drop table if exists social_hub;

CREATE TABLE connected_good (
    id VARCHAR(64) PRIMARY KEY,
    creation_slot BIGINT not null
);

CREATE TABLE connected_good_update (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id VARCHAR(64) not null,
    output_index INT not null,
    slot BIGINT not null,
    connected_good_id VARCHAR(64),
    FOREIGN KEY (connected_good_id) REFERENCES connected_good(id)
);

CREATE TABLE social_hub (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    password varchar(64) not null,
    owner varchar(64),
    picture varchar(32),
    name varchar(160),
    subtitle varchar(160),
    x varchar(160),
    telegram varchar(160),
    instagram varchar(160),
    discord varchar(160),
    reddit varchar(160),
    youtube varchar(160),
    linkedin varchar(160),
    github varchar(160),
    website varchar(160),
    adahandle varchar(160),
    email varchar(160),
    asset_id VARCHAR(64) not null,
    transaction_id VARCHAR(64) not null,
    output_index INT not null,
    creation_slot BIGINT not null,
    batch_id VARCHAR(64),
    FOREIGN KEY (batch_id) REFERENCES connected_good(id)
);