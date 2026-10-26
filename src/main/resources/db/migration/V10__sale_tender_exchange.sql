ALTER TABLE sales ADD COLUMN IF NOT EXISTS tender_amount numeric(18,4);
ALTER TABLE sales ADD COLUMN IF NOT EXISTS tender_currency varchar(8);
ALTER TABLE sales ADD COLUMN IF NOT EXISTS change_text varchar(255);
ALTER TABLE sales ADD COLUMN IF NOT EXISTS change_khr numeric(18,2);
ALTER TABLE sales ADD COLUMN IF NOT EXISTS change_usd numeric(18,4);
