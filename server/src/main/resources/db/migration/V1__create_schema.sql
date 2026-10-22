-- anime is an ID map (internal id <-> platform id) and nothing else: the server stores no anime details
-- (ADR 0007, ADR 0009). The sync API speaks mal_id and rows are created on push, so there is no catalog to seed.
CREATE TABLE anime (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mal_id INTEGER NOT NULL UNIQUE CHECK (mal_id > 0)
);

-- Accounts (ADR 0010). Keyed on Google's `sub`; the server stores no email or other profile data.
CREATE TABLE app_user (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    google_sub TEXT        NOT NULL UNIQUE CHECK (google_sub <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Sync cursor (ADR 0006). updated_at is client-set, so it cannot be a pull cursor: a late push with an old
-- timestamp would land behind cursors other clients already hold. server_seq is assigned by the server on
-- every insert and every applied update, so changesSince(cursor) never misses a row. That holds only because
-- pushes of one user are serialized (advisory lock in SyncRepository.push): commit order must match seq order.
CREATE SEQUENCE list_entry_sync_seq;

-- deleted_at is the tombstone: sync pulls must see deletions, so rows are never hard-deleted.
CREATE TABLE list_entry (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
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

-- Single-use nonces handed out before a Google sign-in. Hard-deleted when consumed or expired.
CREATE TABLE sign_in_nonce (
    nonce      TEXT        PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);

-- Only the SHA-256 of a refresh token is stored. A rotated token keeps its row with revoked_at set, so that
-- presenting it again (token theft) is detectable and revokes the whole family.
CREATE TABLE refresh_token (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    family_id  UUID        NOT NULL,
    token_hash BYTEA       NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX refresh_token_family_idx ON refresh_token (family_id);
