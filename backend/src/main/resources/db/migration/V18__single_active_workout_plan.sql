CREATE UNIQUE INDEX ux_workout_plans_single_active_per_user
    ON workout_plans(user_id)
    WHERE is_active = true;
