-- Deleting a WorkoutGroup (via WorkoutPlan cascade) must also remove
-- any WorkoutSessions and SetLogs that reference it.
ALTER TABLE workout_sessions
    DROP CONSTRAINT workout_sessions_workout_group_id_fkey;

ALTER TABLE workout_sessions
    ADD CONSTRAINT workout_sessions_workout_group_id_fkey
        FOREIGN KEY (workout_group_id) REFERENCES workout_groups(id) ON DELETE CASCADE;
