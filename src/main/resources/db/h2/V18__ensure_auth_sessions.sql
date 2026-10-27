-- Recover databases created without the JDBC-managed authentication table.
CREATE TABLE IF NOT EXISTS auth_sessions (
  access_hash VARCHAR(64) PRIMARY KEY,
  refresh_hash VARCHAR(64) NOT NULL UNIQUE,
  access_expires_at BIGINT NOT NULL,
  session_expires_at BIGINT NOT NULL,
  user_id VARCHAR(36) REFERENCES app_users(id)
);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_id ON auth_sessions(user_id);
