-- Ensure standard roles exist
INSERT INTO roles (name, description, created_at, updated_at, is_removed)
SELECT 'ADMIN', 'System administrator', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');

INSERT INTO roles (name, description, created_at, updated_at, is_removed)
SELECT 'LEARNER', 'Vietnamese learner', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'LEARNER');

INSERT INTO roles (name, description, created_at, updated_at, is_removed)
SELECT 'CONTENT', 'Learning-content manager', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'CONTENT');

-- Drop legacy constraint that prevented 'CONTENT'
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_role;

-- Normalize role values
UPDATE users
SET role = CASE
    WHEN UPPER(COALESCE(role, '')) = 'ADMIN' THEN 'ADMIN'
    WHEN UPPER(COALESCE(role, '')) IN ('CONTENT', 'CONTENT_MANAGER', 'TEACHER', 'INSTRUCTOR') THEN 'CONTENT'
    ELSE 'LEARNER'
END;

-- Add updated constraint allowing both CONTENT and CONTENT_MANAGER
ALTER TABLE users ADD CONSTRAINT ck_users_role
    CHECK (role IN ('LEARNER', 'CONTENT', 'ADMIN', 'CONTENT_MANAGER'));
