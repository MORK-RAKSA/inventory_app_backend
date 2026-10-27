ALTER TABLE store_settings ADD COLUMN currency_mode varchar(10) NOT NULL DEFAULT 'manual';
UPDATE store_settings SET categories_json='[]' WHERE categories_json IS NULL;
UPDATE store_settings SET base_units_json='[]' WHERE base_units_json IS NULL;
