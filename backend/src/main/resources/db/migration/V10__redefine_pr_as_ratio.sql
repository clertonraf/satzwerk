-- Reset all PR flags
UPDATE set_logs SET is_pr = FALSE;

-- Re-backfill: a set is a PR if its weight/reps ratio exceeds
-- all prior sets for the same exercise by the same user (reps > 0 only).
WITH ranked AS (
    SELECT
        sl.id,
        sl.weight / sl.reps AS ratio,
        MAX(sl.weight::numeric / sl.reps) OVER (
            PARTITION BY sl.exercise_id, ws.user_id
            ORDER BY sl.logged_at, sl.id
            ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
        ) AS prev_max_ratio
    FROM set_logs sl
    JOIN workout_sessions ws ON sl.workout_session_id = ws.id
    WHERE sl.reps > 0
)
UPDATE set_logs
SET is_pr = TRUE
FROM ranked
WHERE set_logs.id = ranked.id
  AND (ranked.prev_max_ratio IS NULL OR ranked.ratio > ranked.prev_max_ratio);
