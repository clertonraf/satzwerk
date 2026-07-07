-- Drop the single-column user_id index superseded by the compound indexes below.
DROP INDEX IF EXISTS idx_workout_sessions_user_id;

-- Compound partial index for analytics queries that filter on user_id and completed_at IS NOT NULL.
CREATE INDEX idx_workout_sessions_user_completed
    ON workout_sessions (user_id, completed_at)
    WHERE completed_at IS NOT NULL;

-- Partial index for the open-session lookup (completed_at IS NULL means the session is open).
CREATE INDEX idx_workout_sessions_user_open
    ON workout_sessions (user_id)
    WHERE completed_at IS NULL;
