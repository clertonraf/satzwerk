ALTER TABLE idempotency_records
    ADD COLUMN request_fingerprint TEXT;

UPDATE idempotency_records
SET request_fingerprint = ''
WHERE request_fingerprint IS NULL;

ALTER TABLE idempotency_records
    ALTER COLUMN request_fingerprint SET NOT NULL;

ALTER TABLE partner_write_audit
    ADD COLUMN granted_scopes TEXT;

UPDATE partner_write_audit
SET granted_scopes = ''
WHERE granted_scopes IS NULL;

ALTER TABLE partner_write_audit
    ALTER COLUMN granted_scopes SET NOT NULL;
