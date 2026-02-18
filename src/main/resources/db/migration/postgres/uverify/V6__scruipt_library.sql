CREATE SEQUENCE library_version_seq START WITH 0 MINVALUE 0;

CREATE TABLE library (
    version BIGINT PRIMARY KEY DEFAULT nextval('library_version_seq'),
    compiled_code varchar not null,
    hash varchar(64) not null,
    slot BIGINT not null,
    output_index int not null,
    transaction_id varchar(64) not null
);

ALTER SEQUENCE library_version_seq OWNED BY library.version;