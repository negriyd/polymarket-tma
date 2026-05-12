CREATE TABLE IF NOT EXISTS app_user (
    id              BIGSERIAL PRIMARY KEY,
    telegram_id     BIGINT       NOT NULL UNIQUE,
    username        VARCHAR(64),
    first_name      VARCHAR(128),
    last_name       VARCHAR(128),
    language_code   VARCHAR(8),
    is_premium      BOOLEAN      NOT NULL DEFAULT FALSE,
    photo_url       VARCHAR(512),
    wallet_address  VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_app_user_telegram_id ON app_user (telegram_id);

CREATE TABLE IF NOT EXISTS favorite_market (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    condition_id    VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_favorite UNIQUE (user_id, condition_id)
);

CREATE INDEX IF NOT EXISTS idx_favorite_user ON favorite_market (user_id);

CREATE TABLE IF NOT EXISTS refresh_token (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_user ON refresh_token (user_id);

-- Phase 2 audit table; created up front so the schema is stable.
CREATE TABLE IF NOT EXISTS order_audit (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    order_hash      VARCHAR(128),
    condition_id    VARCHAR(128) NOT NULL,
    side            VARCHAR(8)   NOT NULL,
    maker_amount    NUMERIC(38, 0) NOT NULL,
    taker_amount    NUMERIC(38, 0) NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    idempotency_key VARCHAR(128) UNIQUE,
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_user ON order_audit (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_condition ON order_audit (condition_id);
