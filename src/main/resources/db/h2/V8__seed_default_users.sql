-- Seed default administrator and cashier accounts
INSERT INTO app_users (id, username, email, full_name, password_hash, role, active, created_at, updated_at)
VALUES 
  ('usr-admin-default', 'admin', 'admin@retailflow.local', 'System Administrator', '$2a$12$R.vL0z15pZq9.KkJ2V8zgehD6QoQ.Fh8kF7wz7kM0U8k4YqO1Q9P.', 'ADMIN', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('usr-morkraksa-default', 'morkraksa', 'morkraksa@retailflow.local', 'Mork Raksa', '$2a$12$R.vL0z15pZq9.KkJ2V8zgehD6QoQ.Fh8kF7wz7kM0U8k4YqO1Q9P.', 'ADMIN', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
