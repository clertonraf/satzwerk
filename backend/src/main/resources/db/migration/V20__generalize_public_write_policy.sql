ALTER TABLE idempotency_records
    RENAME TO public_write_idempotency_records;

ALTER TABLE public_write_idempotency_records
    RENAME COLUMN grant_id TO credential_id;

ALTER TABLE public_write_idempotency_records
    ADD COLUMN principal_type TEXT;

UPDATE public_write_idempotency_records
SET principal_type = 'PARTNER_APP'
WHERE principal_type IS NULL;

ALTER TABLE public_write_idempotency_records
    ALTER COLUMN principal_type SET NOT NULL;

ALTER TABLE public_write_idempotency_records
    DROP CONSTRAINT idempotency_records_grant_id_fkey;

ALTER TABLE public_write_idempotency_records
    DROP CONSTRAINT idempotency_records_grant_id_request_method_request_path_idempotency_key_key;

DROP INDEX idx_idempotency_records_grant_id;

ALTER TABLE public_write_idempotency_records
    ADD CONSTRAINT uq_public_write_idempotency_records_identity_key
        UNIQUE (principal_type, credential_id, request_method, request_path, idempotency_key);

CREATE INDEX idx_public_write_idempotency_records_identity
    ON public_write_idempotency_records(principal_type, credential_id);

ALTER TABLE partner_write_audit
    RENAME TO public_write_audit;

ALTER TABLE public_write_audit
    ADD COLUMN principal_type TEXT,
    ADD COLUMN credential_id UUID;

UPDATE public_write_audit
SET principal_type = 'PARTNER_APP',
    credential_id = grant_id
WHERE principal_type IS NULL;

ALTER TABLE public_write_audit
    ALTER COLUMN principal_type SET NOT NULL,
    ALTER COLUMN credential_id SET NOT NULL,
    ALTER COLUMN app_id DROP NOT NULL,
    ALTER COLUMN grant_id DROP NOT NULL;

DROP INDEX idx_partner_write_audit_grant_id;
DROP INDEX idx_partner_write_audit_user_id;

CREATE INDEX idx_public_write_audit_identity
    ON public_write_audit(principal_type, credential_id);

CREATE INDEX idx_public_write_audit_user_id
    ON public_write_audit(user_id);
