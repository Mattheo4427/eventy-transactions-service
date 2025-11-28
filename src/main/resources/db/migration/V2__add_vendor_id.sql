ALTER TABLE transactions ADD COLUMN vendor_id UUID;
CREATE INDEX idx_transactions_vendor ON transactions(vendor_id);
