ALTER TABLE set_logs ADD COLUMN is_pr BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_set_logs_is_pr ON set_logs(exercise_id, is_pr) WHERE is_pr = TRUE;

-- Backfill: mark a set as a PR if its weight exceeds every prior set for the same
-- exercise by the same user (ordered by logged_at). Uses a window function to find
-- the running max weight before each row, then flags rows that beat it.
WITH ranked AS (
    SELECT
        sl.id,
        sl.weight,
        MAX(sl.weight) OVER (
            PARTITION BY sl.exercise_id, ws.user_id
            ORDER BY sl.logged_at
            ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
        ) AS prev_max
    FROM set_logs sl
    JOIN workout_sessions ws ON sl.workout_session_id = ws.id
)
UPDATE set_logs
SET is_pr = TRUE
FROM ranked
WHERE set_logs.id = ranked.id
  AND (ranked.prev_max IS NULL OR ranked.weight > ranked.prev_max);
