-- anime is an ID map (internal id <-> platform id) and nothing else: the server stores no anime details
-- (ADR 0007, ADR 0009). The sync API speaks mal_id and rows are created on push, so there is no catalog to seed.
CREATE TABLE anime (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mal_id INTEGER NOT NULL UNIQUE CHECK (mal_id > 0)
);

-- Sync cursor (ADR 0006). updated_at is client-set, so it cannot be a pull cursor: a late push with an old
-- timestamp would land behind cursors other clients already hold. server_seq is assigned by the server on
-- every insert and every applied update, so changesSince(cursor) never misses a row. That holds only because
-- pushes of one user are serialized (advisory lock in SyncRepository.push): commit order must match seq order.
CREATE SEQUENCE list_entry_sync_seq;

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
    server_seq BIGINT      NOT NULL DEFAULT nextval('list_entry_sync_seq') UNIQUE,
    UNIQUE (user_id, anime_id)
);

ALTER SEQUENCE list_entry_sync_seq OWNED BY list_entry.server_seq;

CREATE INDEX list_entry_user_seq_idx ON list_entry (user_id, server_seq);
