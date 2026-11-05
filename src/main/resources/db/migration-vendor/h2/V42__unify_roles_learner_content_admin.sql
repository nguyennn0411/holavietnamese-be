-- A user has one persisted role only. Conversion priority is:
-- ADMIN (from users.role or users_roles) > CONTENT/CONTENT_MANAGER > LEARNER.
-- This preserves every existing ADMIN account; this migration creates no ADMIN user.

INSERT INTO roles (name, description, created_at, updated_at, is_removed)
SELECT 'ADMIN', 'System administrator', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');

INSERT INTO roles (name, description, created_at, updated_at, is_removed)
SELECT 'LEARNER', 'Vietnamese learner', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'LEARNER');

INSERT INTO roles (name, description, created_at, updated_at, is_removed)
SELECT 'CONTENT', 'Learning-content manager', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'CONTENT');

INSERT INTO role_permissions (role_id, permission)
SELECT content.id, legacy_permissions.permission
FROM roles legacy
JOIN role_permissions legacy_permissions ON legacy_permissions.role_id = legacy.id
JOIN roles content ON content.name = 'CONTENT'
WHERE legacy.name = 'CONTENT_MANAGER'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing_permission
      WHERE existing_permission.role_id = content.id
        AND existing_permission.permission = legacy_permissions.permission
  );

INSERT INTO role_permissions (role_id, permission)
SELECT role_row.id, permissions.permission
FROM roles role_row
JOIN (
    SELECT 'COURSE_READ' AS permission UNION ALL
    SELECT 'COURSE_WRITE' UNION ALL
    SELECT 'COURSE_SUBMIT' UNION ALL
    SELECT 'VOCABULARY_READ' UNION ALL
    SELECT 'VOCABULARY_WRITE' UNION ALL
    SELECT 'VOCABULARY_IMPORT' UNION ALL
    SELECT 'EXPLORE_READ' UNION ALL
    SELECT 'EXPLORE_WRITE' UNION ALL
    SELECT 'EXPLORE_LINK_WRITE'
) permissions
WHERE role_row.name = 'CONTENT'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing_permission
      WHERE existing_permission.role_id = role_row.id
        AND existing_permission.permission = permissions.permission
  );

UPDATE users
SET role = CASE
    WHEN UPPER(COALESCE(role, '')) = 'ADMIN'
         OR EXISTS (
             SELECT 1
             FROM users_roles ur
             JOIN roles r ON r.id = ur.roles_id
             WHERE ur.users_id = users.id AND UPPER(r.name) = 'ADMIN'
         )
        THEN 'ADMIN'
    WHEN UPPER(COALESCE(role, '')) IN ('CONTENT', 'CONTENT_MANAGER')
         OR EXISTS (
             SELECT 1
             FROM users_roles ur
             JOIN roles r ON r.id = ur.roles_id
             WHERE ur.users_id = users.id
               AND UPPER(r.name) IN ('CONTENT', 'CONTENT_MANAGER')
         )
        THEN 'CONTENT'
    ELSE 'LEARNER'
END;

DROP TABLE IF EXISTS users_roles;

DELETE FROM role_permissions
WHERE role_id IN (
    SELECT id FROM roles
    WHERE name NOT IN ('LEARNER', 'CONTENT', 'ADMIN')
);

DELETE FROM roles
WHERE name NOT IN ('LEARNER', 'CONTENT', 'ADMIN');

ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role
    CHECK (role IN ('LEARNER', 'CONTENT', 'ADMIN'));
