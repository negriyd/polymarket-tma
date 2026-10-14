-- Bot messages about markets a user follows: big price moves, markets about to close, a weekly digest.

-- Price of the user's side when we last told them about it (or first saw it): alerts fire on moves
-- from here, so a market drifting 10 points over a week alerts once, not every run.
CREATE TABLE IF NOT EXISTS watch_state (
    user_id           BIGINT         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    condition_id      VARCHAR(80)    NOT NULL,
    last_price        NUMERIC(10, 6) NOT NULL,
    last_alert_at     TIMESTAMPTZ,
    closing_reminded  BOOLEAN        NOT NULL DEFAULT FALSE,
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, condition_id)
);

-- Every watch message sent, for the per-user daily cap; WEEKLY rows carry the week (ref) so a
-- digest goes out once per user per week.
CREATE TABLE IF NOT EXISTS bot_message (
    id       BIGSERIAL PRIMARY KEY,
    user_id  BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind     VARCHAR(16)  NOT NULL,   -- WATCH / WEEKLY
    ref      VARCHAR(32),
    sent_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_bot_message_user_sent ON bot_message (user_id, sent_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_bot_message_weekly ON bot_message (user_id, ref) WHERE kind = 'WEEKLY';
