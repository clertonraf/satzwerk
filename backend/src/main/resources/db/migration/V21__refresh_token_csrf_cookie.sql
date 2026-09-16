ALTER TABLE refresh_tokens
    ADD COLUMN csrf_token_hash TEXT NOT NULL DEFAULT '';
