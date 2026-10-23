CREATE TABLE aym_chapter_progress (
    public_key     VARCHAR(64)  NOT NULL,
    profile_id     VARCHAR(255) NOT NULL,
    course_id      VARCHAR(255) NOT NULL,
    chapter_index  INT          NOT NULL,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (public_key, profile_id, course_id),
    FOREIGN KEY (public_key, profile_id) REFERENCES aym_user_profile(public_key, profile_id) ON DELETE CASCADE
);
