CREATE TABLE personal_api_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    token_hash  TEXT        NOT NULL UNIQUE,
    scopes      TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ
);

CREATE INDEX personal_api_tokens_user_id_idx ON personal_api_tokens (user_id);
CREATE INDEX personal_api_tokens_token_hash_idx ON personal_api_tokens (token_hash);
