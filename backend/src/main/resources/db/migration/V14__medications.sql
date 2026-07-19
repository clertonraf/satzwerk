CREATE TABLE medications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    dosage_amount NUMERIC(10, 4) NOT NULL,
    dosage_unit TEXT NOT NULL,
    frequency JSONB NOT NULL,
    purpose TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX medications_user_name_ci ON medications (user_id, LOWER(name));

CREATE TABLE medication_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    medication_id UUID NOT NULL REFERENCES medications(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL,
    taken_at TIMESTAMPTZ NOT NULL,
    taken BOOLEAN NOT NULL,
    dose_amount NUMERIC(10, 4),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX medication_logs_medication_taken_at ON medication_logs (medication_id, taken_at);
CREATE INDEX medication_logs_user_taken_at ON medication_logs (user_id, taken_at);
