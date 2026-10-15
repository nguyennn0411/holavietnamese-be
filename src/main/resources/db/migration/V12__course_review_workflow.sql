ALTER TABLE courses ADD COLUMN submitted_by BIGINT;
ALTER TABLE courses ADD COLUMN submitted_at TIMESTAMP(6);
ALTER TABLE courses ADD COLUMN reviewed_by BIGINT;
ALTER TABLE courses ADD COLUMN reviewed_at TIMESTAMP(6);
ALTER TABLE courses ADD COLUMN review_note VARCHAR(2000);

ALTER TABLE courses ADD CONSTRAINT fk_course_submitted_by FOREIGN KEY (submitted_by) REFERENCES users(id);
ALTER TABLE courses ADD CONSTRAINT fk_course_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(id);

INSERT INTO roles(name,description)
SELECT 'CONTENT_MANAGER','Quản lý nội dung khóa học'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name='CONTENT_MANAGER');

INSERT INTO role_permissions(role_id,permission)
SELECT r.id,'COURSE_READ' FROM roles r
WHERE r.name='CONTENT_MANAGER'
  AND NOT EXISTS (SELECT 1 FROM role_permissions p WHERE p.role_id=r.id AND p.permission='COURSE_READ');
INSERT INTO role_permissions(role_id,permission)
SELECT r.id,'COURSE_WRITE' FROM roles r
WHERE r.name='CONTENT_MANAGER'
  AND NOT EXISTS (SELECT 1 FROM role_permissions p WHERE p.role_id=r.id AND p.permission='COURSE_WRITE');
INSERT INTO role_permissions(role_id,permission)
SELECT r.id,'COURSE_SUBMIT' FROM roles r
WHERE r.name='CONTENT_MANAGER'
  AND NOT EXISTS (SELECT 1 FROM role_permissions p WHERE p.role_id=r.id AND p.permission='COURSE_SUBMIT');

