ALTER TABLE workout_plans
    ADD COLUMN activated_at TIMESTAMPTZ NULL;

UPDATE workout_plans
SET activated_at = updated_at
WHERE is_active = TRUE;
