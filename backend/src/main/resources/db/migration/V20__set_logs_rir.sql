ALTER TABLE set_logs
    ADD COLUMN rir INTEGER,
    ADD CONSTRAINT chk_set_logs_rir_range CHECK (rir IS NULL OR (rir >= 0 AND rir <= 10));
