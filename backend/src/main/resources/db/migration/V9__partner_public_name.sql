-- Name the partner's audience sees on the author badge and the weekly leaderboard (e.g. the channel title).
ALTER TABLE partner ADD COLUMN IF NOT EXISTS public_name VARCHAR(64);

-- Weekly leaderboards scan resolved predictions by time.
CREATE INDEX IF NOT EXISTS idx_prediction_resolved ON prediction (resolved_at) WHERE resolved_at IS NOT NULL;
