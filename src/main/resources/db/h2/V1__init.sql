CREATE TABLE store_settings (
  id bigint PRIMARY KEY CHECK (id=1),
  usd_to_khr numeric(18,4) NOT NULL CHECK (usd_to_khr > 0),
  updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO store_settings(id,usd_to_khr) VALUES (1,4100);
CREATE TABLE products (
  id uuid PRIMARY KEY,
  name varchar(200) NOT NULL,
  barcode varchar(100) UNIQUE,
  category varchar(100) NOT NULL,
  price_usd numeric(18,4) NOT NULL CHECK (price_usd >= 0),
  original_price numeric(18,4) NOT NULL,
  original_ccy char(3) NOT NULL CHECK(original_ccy IN ('USD','KHR')),
  price_fx_rate numeric(18,4) NOT NULL,
  stock integer NOT NULL CHECK (stock >= 0),
  low_stock_threshold integer NOT NULL DEFAULT 5,
  archived boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_products_category ON products(category);
CREATE TABLE sales (
  id uuid PRIMARY KEY,
  idempotency_key varchar(150) NOT NULL UNIQUE,
  created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  rate_snapshot numeric(18,4) NOT NULL,
  total_usd numeric(18,4) NOT NULL,
  total_khr numeric(18,2) NOT NULL,
  paid_currency char(3) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'COMPLETED'
);
CREATE TABLE sale_items (
  id uuid PRIMARY KEY,
  sale_id uuid NOT NULL REFERENCES sales(id),
  product_id uuid NOT NULL REFERENCES products(id),
  product_name varchar(200) NOT NULL,
  quantity integer NOT NULL CHECK (quantity > 0),
  unit_price_usd numeric(18,4) NOT NULL,
  line_total_usd numeric(18,4) NOT NULL
);
CREATE INDEX idx_sales_date ON sales(created_at);
CREATE TABLE stock_movements (
  id uuid PRIMARY KEY,
  product_id uuid NOT NULL REFERENCES products(id),
  quantity_delta integer NOT NULL,
  reason varchar(50) NOT NULL,
  reference_id uuid,
  created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
