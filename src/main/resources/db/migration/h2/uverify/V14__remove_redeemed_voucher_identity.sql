DROP INDEX IF EXISTS idx_aym_redeemed_by;

CREATE TABLE aym_redeemed_voucher_new (
    id          UUID         PRIMARY KEY,
    content_id  VARCHAR(255) NOT NULL,
    redeemed_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO aym_redeemed_voucher_new (id, content_id, redeemed_at)
SELECT id, content_id, redeemed_at FROM aym_redeemed_voucher;

DROP TABLE aym_redeemed_voucher;

ALTER TABLE aym_redeemed_voucher_new RENAME TO aym_redeemed_voucher;
