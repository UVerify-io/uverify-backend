DROP INDEX IF EXISTS idx_aym_redeemed_by;
ALTER TABLE aym_redeemed_voucher DROP COLUMN IF EXISTS redeemed_by_public_key CASCADE;
ALTER TABLE aym_redeemed_voucher DROP COLUMN IF EXISTS redeemed_by_profile_id;
