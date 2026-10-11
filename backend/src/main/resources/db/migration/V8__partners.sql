-- Partner program: channel owners with individual terms, set by the operator.
-- Overrides the default referral share/duration for users this partner invites.
CREATE TABLE IF NOT EXISTS partner (
    user_id     BIGINT       PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    -- Partner's share of the platform trading fee (5000 = 50% of the fee).
    share_bps   INT          NOT NULL CHECK (share_bps BETWEEN 0 AND 10000),
    -- How long after each invited user's sign-up the partner earns; NULL = lifetime.
    months      INT          CHECK (months IS NULL OR months > 0),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    note        VARCHAR(256),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
