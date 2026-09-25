CREATE TABLE IF NOT EXISTS fx_auth_accounts (
    username VARCHAR(64) PRIMARY KEY,
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    iterations INTEGER NOT NULL,
    last_uuid VARCHAR(36),
    registered_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    last_login_at BIGINT NOT NULL
);
