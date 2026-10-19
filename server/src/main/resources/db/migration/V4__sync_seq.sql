-- Sync cursor (ADR 0006). updated_at is client-set, so it cannot be a pull cursor: a late push with an old
-- timestamp would land behind cursors other clients already hold. server_seq is assigned by the server on
-- every insert and every applied update, so changesSince(cursor) never misses a row. That holds only because
-- pushes of one user are serialized (advisory lock in SyncRepository.push): commit order must match seq order.
CREATE SEQUENCE list_entry_sync_seq;

ALTER TABLE list_entry ADD COLUMN server_seq BIGINT;

UPDATE list_entry SET server_seq = nextval('list_entry_sync_seq');

ALTER TABLE list_entry
    ALTER COLUMN server_seq SET NOT NULL,
    ALTER COLUMN server_seq SET DEFAULT nextval('list_entry_sync_seq'),
    ADD CONSTRAINT list_entry_server_seq_key UNIQUE (server_seq);

ALTER SEQUENCE list_entry_sync_seq OWNED BY list_entry.server_seq;

CREATE INDEX list_entry_user_seq_idx ON list_entry (user_id, server_seq);

-- Pulls filter by (user_id, server_seq) now; nothing queries by client updated_at.
DROP INDEX list_entry_user_updated_idx;
