-- Rename the back-office authoring role while preserving every existing account
-- and permission assignment. Legacy values are accepted only during migration.
INSERT INTO roles (name, description, created_at, updated_at, is_removed)
SELECT 'CONTENT_CREATOR', 'Learning-content creator', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'CONTENT_CREATOR');

INSERT IGNORE INTO role_permissions (role_id, permission)
SELECT creator.id, legacy_permissions.permission
FROM roles legacy
JOIN role_permissions legacy_permissions ON legacy_permissions.role_id = legacy.id
JOIN roles creator ON creator.name = 'CONTENT_CREATOR'
WHERE legacy.name IN ('CONTENT', 'CONTENT_MANAGER');

ALTER TABLE users DROP CONSTRAINT ck_users_role;

UPDATE users
SET role = CASE
    WHEN UPPER(COALESCE(role, '')) IN ('CONTENT', 'CONTENT_MANAGER', 'CONTENT_CREATOR', 'TEACHER', 'INSTRUCTOR')
        THEN 'CONTENT_CREATOR'
    WHEN UPPER(COALESCE(role, '')) = 'ADMIN' THEN 'ADMIN'
    ELSE 'LEARNER'
END;

DELETE rp
FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id
WHERE r.name IN ('CONTENT', 'CONTENT_MANAGER');

DELETE FROM roles
WHERE name IN ('CONTENT', 'CONTENT_MANAGER');

ALTER TABLE users ADD CONSTRAINT ck_users_role
    CHECK (role IN ('LEARNER', 'CONTENT_CREATOR', 'ADMIN'));
