-- Reset PR flags only on rows currently marked as PRs
UPDATE set_logs SET is_pr = FALSE WHERE is_pr = TRUE;

-- Re-backfill: a set is a PR if its weight/reps ratio exceeds
-- all prior sets for the same exercise by the same user (reps > 0 only).
WITH ranked AS (
    SELECT
        sl.id,
        ROUND(sl.weight / sl.reps, 10) AS ratio,
        MAX(ROUND(sl.weight::numeric / sl.reps, 10)) OVER (
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
