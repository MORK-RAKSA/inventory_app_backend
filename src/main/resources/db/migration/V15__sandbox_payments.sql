CREATE TABLE sandbox_payments (
  id varchar(20) PRIMARY KEY,
  request_id varchar(36) NOT NULL UNIQUE,
  merchant_id varchar(100) NOT NULL,
  amount numeric(10,2) NOT NULL CHECK(amount>0 AND amount<=10),
  status varchar(20) NOT NULL,
  qr_image text,
  expires_at bigint NOT NULL
);
