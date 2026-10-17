-- V1: who lets whom receive from their sorters and pads. UUIDs are 16 bytes, times are epoch milliseconds.
CREATE TABLE framesort_trust (
    owner      BINARY(16) NOT NULL,
    trusted    BINARY(16) NOT NULL,
    created_at BIGINT     NOT NULL,
    PRIMARY KEY (owner, trusted)
) ENGINE = InnoDB;
