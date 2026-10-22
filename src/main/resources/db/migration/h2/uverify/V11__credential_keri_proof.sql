ALTER TABLE uverify_credential ADD COLUMN keri_proof VARCHAR(200);

-- keri_verified was set without checking the binding proof, so no stored value can be trusted.
UPDATE uverify_credential SET keri_verified = FALSE;
