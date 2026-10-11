-- Referral program and launch attribution (which link / channel brought each app open).
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS ref_code VARCHAR(16) UNIQUE;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS referred_by BIGINT REFERENCES app_user (id) ON DELETE SET NULL;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS referred_at TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS signup_source VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_app_user_referred_by ON app_user (referred_by);

-- One row per app launch from Telegram; start_param is the raw ?startapp= value.
CREATE TABLE IF NOT EXISTS app_launch (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    new_user    BOOLEAN     NOT NULL,
    source      VARCHAR(32),
    ref_code    VARCHAR(16),
    market_id   VARCHAR(32),
    start_param VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_app_launch_source ON app_launch (source, created_at);

-- Referrer's share of a trading fee, recorded when the fee batch is submitted to the relayer.
CREATE TABLE IF NOT EXISTS referral_reward (
    id             BIGSERIAL PRIMARY KEY,
    referrer_id    BIGINT         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    referee_id     BIGINT         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    amount_micro   NUMERIC(38, 0) NOT NULL,
    relayer_tx_id  VARCHAR(64),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_referral_reward_referrer ON referral_reward (referrer_id);
