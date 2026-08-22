CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grant_id UUID NOT NULL REFERENCES app_grants(id) ON DELETE CASCADE,
    request_method TEXT NOT NULL,
    request_path TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    response_status INTEGER NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (grant_id, request_method, request_path, idempotency_key)
);

CREATE INDEX idx_idempotency_records_grant_id ON idempotency_records(grant_id);

CREATE TABLE partner_write_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grant_id UUID NOT NULL REFERENCES app_grants(id) ON DELETE CASCADE,
    app_id UUID NOT NULL REFERENCES partner_apps(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_method TEXT NOT NULL,
    request_path TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    response_status INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_partner_write_audit_grant_id ON partner_write_audit(grant_id);
CREATE INDEX idx_partner_write_audit_user_id ON partner_write_audit(user_id);
