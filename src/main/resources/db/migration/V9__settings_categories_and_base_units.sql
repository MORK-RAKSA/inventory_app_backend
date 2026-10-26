ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS categories_json text;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS base_units_json text;
