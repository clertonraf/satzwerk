-- Partner apps registered by third-party developers
CREATE TABLE partner_apps (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name          TEXT        NOT NULL,
    description   TEXT        NOT NULL,
    redirect_uri  TEXT        NOT NULL,
    client_id     TEXT        NOT NULL UNIQUE,
    -- bcrypt hash of the issued client secret; never stored in plaintext
    client_secret_hash TEXT   NOT NULL,
    -- space-separated list of declared scopes
    scopes        TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- User consent grants: a user authorises a partner app for declared scopes
CREATE TABLE app_grants (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    app_id            UUID        NOT NULL REFERENCES partner_apps(id) ON DELETE CASCADE,
    user_id           UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- subset of partner_apps.scopes the user explicitly granted
    granted_scopes    TEXT        NOT NULL,
    access_token_hash TEXT        NOT NULL UNIQUE,
    granted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- NULL = active; non-NULL = immediately revoked
    revoked_at        TIMESTAMPTZ,
    -- audit: who initiated revocation ('user' or 'admin')
    revoked_by        TEXT,
    UNIQUE (app_id, user_id)
);

CREATE INDEX idx_app_grants_user_id       ON app_grants(user_id);
CREATE INDEX idx_app_grants_app_id        ON app_grants(app_id);
CREATE INDEX idx_app_grants_token_hash    ON app_grants(access_token_hash);
