-- Phase 2: on-chain withdraw audit trail.

CREATE TABLE IF NOT EXISTS withdrawal (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    amount_micro    NUMERIC(38, 0) NOT NULL,
    tx_hash         VARCHAR(128),
    status          VARCHAR(32)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_withdrawal_user_created ON withdrawal (user_id, created_at DESC);
