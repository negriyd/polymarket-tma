-- Platform trading fees, one row per fee batch the user signed and the relayer accepted.
CREATE TABLE IF NOT EXISTS fee_payment (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    platform_amount    NUMERIC(38, 0) NOT NULL,
    referrer_id        BIGINT         REFERENCES app_user (id) ON DELETE SET NULL,
    referrer_amount    NUMERIC(38, 0) NOT NULL DEFAULT 0,
    relayer_tx_id      VARCHAR(64),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_fee_payment_created ON fee_payment (created_at);
