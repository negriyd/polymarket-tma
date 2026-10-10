-- Internal parimutuel bets (Phase 1). CLOB orders remain in order_audit (legacy).

CREATE TABLE IF NOT EXISTS platform_market (
    condition_id            VARCHAR(128) PRIMARY KEY,
    question                TEXT,
    end_date                TIMESTAMPTZ,
    status                  VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    winning_outcome_index   SMALLINT,
    resolved_at             TIMESTAMPTZ,
    resolution_source       VARCHAR(16),
    enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_platform_market_status ON platform_market (status);
CREATE INDEX IF NOT EXISTS idx_platform_market_end_date ON platform_market (end_date);

CREATE TABLE IF NOT EXISTS bet (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    condition_id        VARCHAR(128) NOT NULL,
    outcome_index       SMALLINT     NOT NULL,
    amount_micro_usdc   NUMERIC(38, 0) NOT NULL,
    payment_tx_hash     VARCHAR(128) UNIQUE,
    status              VARCHAR(32)  NOT NULL,
    idempotency_key     VARCHAR(128) UNIQUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    confirmed_at        TIMESTAMPTZ,
    CONSTRAINT fk_bet_platform_market FOREIGN KEY (condition_id)
        REFERENCES platform_market (condition_id)
);

CREATE INDEX IF NOT EXISTS idx_bet_user ON bet (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_bet_condition ON bet (condition_id, status);
CREATE INDEX IF NOT EXISTS idx_bet_status ON bet (status);

CREATE TABLE IF NOT EXISTS user_balance (
    user_id               BIGINT PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    available_micro_usdc  NUMERIC(38, 0) NOT NULL DEFAULT 0,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Reserved for Phase 2 (resolution payouts); schema created now for stability.
CREATE TABLE IF NOT EXISTS payout (
    id              BIGSERIAL PRIMARY KEY,
    bet_id          BIGINT       NOT NULL UNIQUE REFERENCES bet (id) ON DELETE CASCADE,
    user_id         BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    gross_micro     NUMERIC(38, 0) NOT NULL,
    fee_micro       NUMERIC(38, 0) NOT NULL,
    net_micro       NUMERIC(38, 0) NOT NULL,
    credited_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payout_user ON payout (user_id);
