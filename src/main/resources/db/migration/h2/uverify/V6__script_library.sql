CREATE TABLE library (
    version BIGINT AUTO_INCREMENT(0) PRIMARY KEY,
    compiled_code varchar not null,
    hash varchar(64) not null,
    slot BIGINT not null,
    output_index int not null,
    transaction_id varchar(64) not null
);