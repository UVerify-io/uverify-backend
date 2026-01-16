ALTER TABLE bootstrap_datum
  ADD COLUMN version INT NOT NULL DEFAULT 1;

ALTER TABLE state_datum
  ADD COLUMN version INT NOT NULL DEFAULT 1;

ALTER TABLE uverify_certificate
  DROP COLUMN output_index;