-- V1: who lets whom receive from their sorters and pads. UUIDs are 16 bytes, times are epoch milliseconds.
CREATE TABLE framesort_trust (
    owner      BLOB    NOT NULL,
    trusted    BLOB    NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (owner, trusted)
) WITHOUT ROWID;
