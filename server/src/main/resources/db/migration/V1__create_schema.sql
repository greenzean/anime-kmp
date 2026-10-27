CREATE TABLE anime (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mal_id INTEGER NOT NULL UNIQUE CHECK (mal_id > 0)
);

CREATE TABLE app_user (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    google_sub TEXT        NOT NULL UNIQUE CHECK (google_sub <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE SEQUENCE list_entry_sync_seq;

CREATE TABLE list_entry (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    anime_id   BIGINT      NOT NULL REFERENCES anime (id),
    status     TEXT        NOT NULL
        CHECK (status IN ('WATCHING', 'COMPLETED', 'ON_HOLD', 'DROPPED', 'PLAN_TO_WATCH')),
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    progress   INT         NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 10000),
    score      INT         CHECK (score BETWEEN 1 AND 10),
    server_seq BIGINT      NOT NULL DEFAULT nextval('list_entry_sync_seq') UNIQUE,
    UNIQUE (user_id, anime_id)
);

ALTER SEQUENCE list_entry_sync_seq OWNED BY list_entry.server_seq;

CREATE INDEX list_entry_user_seq_idx ON list_entry (user_id, server_seq);

CREATE TABLE sign_in_nonce (
    nonce      TEXT        PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);

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
