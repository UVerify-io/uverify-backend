drop table if exists tadamon_transactions;

CREATE TABLE tadamon_transaction (
    id BIGSERIAL PRIMARY KEY,
    slot BIGINT not null,
    transaction_hex TEXT not null,
    transaction_id VARCHAR(64) not null,
    cso_name VARCHAR(128) not null,
    cso_email VARCHAR(128) not null,
    cso_establishment_date TIMESTAMP not null,
    cso_organization_type VARCHAR(128) not null,
    cso_registration_country VARCHAR(128) not null,
    cso_status_approved BOOLEAN not null,
    tadamon_id VARCHAR(64) not null,
    veridian_aid VARCHAR(64) not null,
    undp_signing_date TIMESTAMP not null,
    beneficiary_signing_date TIMESTAMP not null,
    certificate_creation_date TIMESTAMP not null
);