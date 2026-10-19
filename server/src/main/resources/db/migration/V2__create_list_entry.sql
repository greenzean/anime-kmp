-- user_id is a placeholder until auth lands (no users table yet).
-- deleted_at is the tombstone: sync pulls must see deletions, so rows are never hard-deleted.
CREATE TABLE list_entry (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL,
    anime_id   BIGINT      NOT NULL REFERENCES anime (id),
    status     TEXT        NOT NULL
        CHECK (status IN ('WATCHING', 'COMPLETED', 'ON_HOLD', 'DROPPED', 'PLAN_TO_WATCH')),
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    UNIQUE (user_id, anime_id)
);

CREATE INDEX list_entry_user_updated_idx ON list_entry (user_id, updated_at);
