CREATE UNIQUE INDEX uq_workout_sessions_one_open_per_user
    ON workout_sessions (user_id)
    WHERE completed_at IS NULL;
