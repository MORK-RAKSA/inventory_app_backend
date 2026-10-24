ALTER TABLE products ADD COLUMN wholesale_price_usd numeric(18,4);
ALTER TABLE products ADD COLUMN wholesale_min_qty integer NOT NULL DEFAULT 1;
ALTER TABLE sales ADD COLUMN price_tier varchar(20) NOT NULL DEFAULT 'RETAIL';
