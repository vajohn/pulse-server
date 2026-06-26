-- Dual-control approval lifecycle for psychometric tests.
-- TestStatus.PENDING_APPROVAL and PermissionName.ASSESS_APPROVE are enum values,
-- synced into varchar columns / the permissions table on boot.

-- Widen chk_test_status so PENDING_APPROVAL (introduced in this feature branch) is allowed.
-- The constraint was originally defined in V1 as ('DRAFT','ACTIVE','RETIRED'); any status the
-- Java TestStatus enum carries must be listed here.
ALTER TABLE psychometric_test
    DROP CONSTRAINT IF EXISTS chk_test_status;
ALTER TABLE psychometric_test
    ADD CONSTRAINT chk_test_status
        CHECK (status IN ('DRAFT','PENDING_APPROVAL','ACTIVE','RETIRED'));

-- Add an explicit CHECK on test_approval_request.status to match TestApprovalStatus enum.
ALTER TABLE test_approval_request
    DROP CONSTRAINT IF EXISTS chk_approval_status;
ALTER TABLE test_approval_request
    ADD CONSTRAINT chk_approval_status
        CHECK (status IN ('PENDING','APPROVED','REJECTED'));

ALTER TABLE psychometric_test
    ADD COLUMN supersedes_id UUID NULL
    REFERENCES psychometric_test (id);

CREATE TABLE test_approval_request (
    id                  UUID PRIMARY KEY,
    test_id             UUID NOT NULL REFERENCES psychometric_test (id),
    test_version        INT  NOT NULL,
    submitted_by        UUID NOT NULL REFERENCES users (id),
    submitted_at        TIMESTAMP NOT NULL DEFAULT now(),
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reviewed_by         UUID NULL REFERENCES users (id),
    reviewed_at         TIMESTAMP NULL,
    approval_reference  TEXT NULL,
    review_comment      TEXT NULL
);

CREATE INDEX idx_test_approval_status ON test_approval_request (status);
CREATE INDEX idx_test_approval_test   ON test_approval_request (test_id);

-- At most one PENDING request per (test, version) — enforces the "one open request" rule.
CREATE UNIQUE INDEX idx_test_approval_open
    ON test_approval_request (test_id, test_version)
    WHERE status = 'PENDING';
