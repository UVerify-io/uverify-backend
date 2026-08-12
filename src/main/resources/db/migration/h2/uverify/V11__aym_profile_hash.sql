ALTER TABLE aym_user_profile ADD COLUMN profile_hash VARCHAR(56);
CREATE INDEX idx_aym_profile_hash ON aym_user_profile(profile_hash);
