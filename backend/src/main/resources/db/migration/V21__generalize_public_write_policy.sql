ALTER TABLE idempotency_records
    RENAME TO public_write_idempotency_records;

ALTER TABLE public_write_idempotency_records
    RENAME COLUMN grant_id TO credential_id;

ALTER TABLE public_write_idempotency_records
    ADD COLUMN principal_type TEXT,
    ADD COLUMN app_id UUID REFERENCES partner_apps(id) ON DELETE CASCADE,
    ADD COLUMN grant_id UUID REFERENCES app_grants(id) ON DELETE CASCADE,
    ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE CASCADE;

UPDATE public_write_idempotency_records
SET principal_type = 'PARTNER_APP',
    app_id = app_grants.app_id,
    grant_id = app_grants.id,
    user_id = app_grants.user_id
FROM app_grants
WHERE public_write_idempotency_records.credential_id = app_grants.id
  AND public_write_idempotency_records.principal_type IS NULL;

ALTER TABLE public_write_idempotency_records
    ALTER COLUMN principal_type SET NOT NULL,
    ALTER COLUMN user_id SET NOT NULL;

UPDATE public_write_idempotency_records
SET request_fingerprint = '__legacy_no_fingerprint__'
WHERE response_status <> -1
  AND request_fingerprint = '';

ALTER TABLE public_write_idempotency_records
    DROP CONSTRAINT idempotency_records_grant_id_fkey;

DO $$
DECLARE legacy_constraint_name TEXT;
BEGIN
    FOR legacy_constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        JOIN unnest(c.conkey) WITH ORDINALITY AS cols(attnum, ordinality) ON true
        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = cols.attnum
        WHERE n.nspname = current_schema()
          AND t.relname = 'public_write_idempotency_records'
          AND c.contype = 'u'
        GROUP BY c.conname
        HAVING string_agg(a.attname, ',' ORDER BY cols.ordinality) =
            'credential_id,request_method,request_path,idempotency_key'
    LOOP
        EXECUTE format(
            'ALTER TABLE public_write_idempotency_records DROP CONSTRAINT %I',
            legacy_constraint_name
        );
    END LOOP;
END $$;

DROP INDEX IF EXISTS idx_idempotency_records_grant_id;

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
