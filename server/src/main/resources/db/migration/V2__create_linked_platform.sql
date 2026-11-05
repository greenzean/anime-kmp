CREATE TABLE linked_platform (
    user_id  UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    platform TEXT NOT NULL CHECK (platform IN ('MAL')),
    PRIMARY KEY (user_id, platform)
);
