ALTER TABLE bootstrap_datum
  ADD COLUMN version INT NOT NULL DEFAULT 1;

ALTER TABLE bootstrap_datum ALTER COLUMN authorization_token_script_hash VARCHAR(64) NULL;
ALTER TABLE bootstrap_datum ALTER COLUMN update_token_contract_credential VARCHAR(64) NULL;

ALTER TABLE state_datum
  ADD COLUMN version INT NOT NULL DEFAULT 1;

ALTER TABLE uverify_certificate
  DROP COLUMN output_index;