-- Keep PayPal's approve link, so pressing "Fund" again before approving returns the same order
-- instead of creating a second one.
ALTER TABLE payments ADD COLUMN approve_url text;
