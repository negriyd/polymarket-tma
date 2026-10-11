-- Free predictions, resolved real-bet results and result notifications.

-- Telegram reported allows_write_to_pm (or the user granted requestWriteAccess); cleared on 403.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS allows_messages BOOLEAN NOT NULL DEFAULT FALSE;

-- "Прогноз без денег": one pick per user and market, changeable while the market is open.
CREATE TABLE IF NOT EXISTS prediction (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    condition_id   VARCHAR(80)   NOT NULL,
    market_id      VARCHAR(32)   NOT NULL,
    outcome_index  SMALLINT      NOT NULL,
    outcome_label  VARCHAR(128)  NOT NULL,
    price          NUMERIC(10, 6),
    question       TEXT          NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    resolved_at    TIMESTAMPTZ,
    correct        BOOLEAN,
    UNIQUE (user_id, condition_id)
);
CREATE INDEX IF NOT EXISTS idx_prediction_open ON prediction (condition_id) WHERE resolved_at IS NULL;

-- Outcome of a user's real position once its market resolved (won = held the winning outcome).
CREATE TABLE IF NOT EXISTS bet_result (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    condition_id  VARCHAR(80)     NOT NULL,
    outcome_index SMALLINT        NOT NULL,
    won           BOOLEAN         NOT NULL,
    payout_usdc   NUMERIC(20, 6)  NOT NULL DEFAULT 0,
    resolved_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, condition_id, outcome_index)
);

-- Messages about results; PENDING until sent, merged into a digest, or skipped.
CREATE TABLE IF NOT EXISTS result_notification (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind          VARCHAR(24)   NOT NULL,   -- PREDICTION_CORRECT / PREDICTION_WRONG / BET_WON
    condition_id  VARCHAR(80)   NOT NULL,
    market_id     VARCHAR(32),
    question      TEXT          NOT NULL,
    outcome_label VARCHAR(128)  NOT NULL,
    payout_usdc   NUMERIC(20, 6),
    status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING',  -- PENDING / SENT / DIGEST / SKIPPED / FAILED
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    sent_at       TIMESTAMPTZ,
    UNIQUE (user_id, kind, condition_id)
);
CREATE INDEX IF NOT EXISTS idx_result_notification_pending ON result_notification (user_id) WHERE status = 'PENDING';
