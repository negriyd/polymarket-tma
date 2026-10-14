-- Forecasting leaderboards inside Telegram groups the bot was added to.

CREATE TABLE IF NOT EXISTS group_chat (
    chat_id        BIGINT PRIMARY KEY,           -- Telegram chat id (negative for groups)
    title          TEXT,
    language_code  VARCHAR(8),                   -- of whoever added the bot: language of group posts
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_weekly    VARCHAR(16),                  -- ISO week of the last weekly post
    added_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- App users who joined a group's leaderboard (via /join or the group's app link).
CREATE TABLE IF NOT EXISTS group_member (
    chat_id    BIGINT       NOT NULL REFERENCES group_chat (chat_id) ON DELETE CASCADE ON UPDATE CASCADE,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    joined_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (chat_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_group_member_user ON group_member (user_id);
