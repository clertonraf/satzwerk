CREATE TABLE workout_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workout_group_id UUID NOT NULL REFERENCES workout_groups(id),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    notes TEXT,
    CONSTRAINT one_open_session_per_user UNIQUE (user_id, completed_at) DEFERRABLE
);

CREATE INDEX idx_workout_sessions_user_id ON workout_sessions(user_id);

CREATE TABLE set_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workout_session_id UUID NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL REFERENCES exercises(id),
    set_number INTEGER NOT NULL,
    weight NUMERIC(6,2) NOT NULL,
    reps INTEGER NOT NULL,
    logged_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_set_logs_session_id ON set_logs(workout_session_id);
CREATE INDEX idx_set_logs_exercise_id ON set_logs(exercise_id);
