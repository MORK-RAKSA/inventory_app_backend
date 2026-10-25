-- UOM packaging configuration templates
CREATE TABLE IF NOT EXISTS uom_configs (
  id varchar(100) PRIMARY KEY,
  name varchar(200) NOT NULL,
  base_unit varchar(100) NOT NULL,
  case_unit_name varchar(100) NOT NULL,
  case_qty integer NOT NULL CHECK (case_qty > 0),
  description text,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- Packaging and wholesale fields for products
ALTER TABLE products ADD COLUMN IF NOT EXISTS base_unit varchar(100) DEFAULT 'Piece';
ALTER TABLE products ADD COLUMN IF NOT EXISTS case_unit_name varchar(100) DEFAULT 'Case';
ALTER TABLE products ADD COLUMN IF NOT EXISTS case_qty integer DEFAULT 24;
ALTER TABLE products ADD COLUMN IF NOT EXISTS wholesale_price_per_case_usd numeric(18,4);

-- Packaging and loose quantities for sale items
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS cases integer DEFAULT 0;
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS loose integer DEFAULT 0;
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS case_unit_name varchar(100);
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS base_unit varchar(100);
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS uom varchar(200);
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS is_wholesale boolean DEFAULT false;

-- Seed default reusable UOM templates
INSERT INTO uom_configs (id, name, base_unit, case_unit_name, case_qty, description) VALUES
  ('uom-case-24', 'Case of 24', 'Can', 'Case', 24, 'Standard 24-can beverage or grocery case'),
  ('uom-case-12', 'Case of 12', 'Bottle', 'Case', 12, '12-bottle beverage, oil, or sauce case'),
  ('uom-pack-6', 'Pack of 6', 'Bottle', 'Pack', 6, '6-pack shrink bundle or six-pack carton'),
  ('uom-box-1', 'Box of 1', 'Piece', 'Box', 1, 'Single individual packaged box or piece'),
  ('uom-carton-48', 'Carton of 48', 'Piece', 'Carton', 48, 'Bulk 48-unit master shipping carton')
ON CONFLICT (id) DO NOTHING;
