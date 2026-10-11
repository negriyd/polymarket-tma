-- order_audit.condition_id used to receive the outcome token id. Keep the token id in its own
-- column and the market's condition id in condition_id; old rows don't know their market.
ALTER TABLE order_audit ADD COLUMN IF NOT EXISTS token_id VARCHAR(128);

UPDATE order_audit SET token_id = condition_id WHERE condition_id NOT LIKE '0x%';

ALTER TABLE order_audit ALTER COLUMN condition_id DROP NOT NULL;
UPDATE order_audit SET condition_id = NULL WHERE condition_id NOT LIKE '0x%';

UPDATE order_audit SET side = CASE side WHEN '0' THEN 'BUY' WHEN '1' THEN 'SELL' ELSE side END;
