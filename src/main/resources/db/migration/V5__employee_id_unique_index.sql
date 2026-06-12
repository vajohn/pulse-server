-- STRICT X4Auth login matches users by employee_id (the SAP person id). That lookup returns a
-- single User and runs on every login, so employee_id must be unique and indexed. It mirrors the
-- existing UNIQUE constraint on sf_user_id (which holds the same value). Partial index excludes the
-- many NULL employee_ids (login-only / non-synced accounts).
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_employee_id
    ON users (employee_id)
    WHERE employee_id IS NOT NULL;
