ALTER TABLE uverify_credential ADD COLUMN keri_proof VARCHAR(200);

-- keri_verified was set without checking the binding proof, so no stored value can be trusted.
UPDATE uverify_credential SET keri_verified = FALSE;

UPDATE uverify_credential credential
SET keri_proof = certificate.extra::json ->> 'p'
FROM uverify_certificate certificate
WHERE certificate.hash = credential.auth_cert_hash
  AND certificate.transaction_id = credential.tx_hash
  AND certificate.payment_credential = credential.payment_credential;
