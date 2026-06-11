-- Login/auto-provision roles not present in the squashed V1 baseline.
-- x4auth auto-provisions new users as EMPLOYEE; the saf-recon sync toggles MANAGER.
-- Idempotent: roles.name is UNIQUE, so ON CONFLICT (name) makes re-runs safe.
INSERT INTO roles (id, name)
VALUES (gen_random_uuid(), 'EMPLOYEE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO roles (id, name)
VALUES (gen_random_uuid(), 'MANAGER')
ON CONFLICT (name) DO NOTHING;
