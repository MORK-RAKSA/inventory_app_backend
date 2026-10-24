-- Product images (optional Cloudflare R2 storage).
ALTER TABLE products ADD COLUMN image_url varchar(600);
ALTER TABLE products ADD COLUMN image_key varchar(300);

-- A barcode must be unique only among ACTIVE products, so an archived product
-- no longer blocks re-creating an item with the same barcode.
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_barcode_key;
CREATE UNIQUE INDEX ux_products_barcode_active ON products (barcode) WHERE archived = false;
