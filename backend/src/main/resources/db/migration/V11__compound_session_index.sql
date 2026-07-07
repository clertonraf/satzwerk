-- Compound partial index to speed up analytics queries that filter on both
-- user_id and completed_at IS NOT NULL. Replaces the need to scan all sessions
-- for a user and then filter by completion status.
CREATE INDEX idx_workout_sessions_user_completed
    ON workout_sessions (user_id, completed_at)
    WHERE completed_at IS NOT NULL;
