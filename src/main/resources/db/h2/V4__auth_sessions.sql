CREATE TABLE IF NOT EXISTS auth_sessions (
  access_hash VARCHAR(64) PRIMARY KEY,
  refresh_hash VARCHAR(64) NOT NULL UNIQUE,
  access_expires_at BIGINT NOT NULL,
  session_expires_at BIGINT NOT NULL
);
