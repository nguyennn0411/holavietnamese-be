-- Add Huy's profile/role model to the existing user identity; preserve all learning FKs.
ALTER TABLE users ADD COLUMN username VARCHAR(254) UNIQUE;
ALTER TABLE users ADD COLUMN full_name VARCHAR(100);
ALTER TABLE users ADD COLUMN phone_number VARCHAR(20);
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(500);
ALTER TABLE users ADD COLUMN native_language VARCHAR(50);
ALTER TABLE users ADD COLUMN learning_goal VARCHAR(255);
ALTER TABLE users ADD COLUMN target_level VARCHAR(20);
ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE users ADD COLUMN is_removed BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users SET username=email, status=CASE WHEN enabled THEN 'ACTIVE' ELSE 'INACTIVE' END;
CREATE TABLE roles (
 id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50) UNIQUE, description VARCHAR(255),
 created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
 is_removed BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE users_roles (
 users_id BIGINT NOT NULL, roles_id INT NOT NULL,
 PRIMARY KEY(users_id,roles_id),
 CONSTRAINT fk_user_role_user FOREIGN KEY(users_id) REFERENCES users(id),
 CONSTRAINT fk_user_role_role FOREIGN KEY(roles_id) REFERENCES roles(id)
);
INSERT INTO roles(name,description) VALUES('ADMIN','Administrator'),('LEARNER','Learner'),('TEACHER','Teacher');
INSERT INTO users_roles(users_id,roles_id)
 SELECT u.id,r.id FROM users u JOIN roles r ON r.name=u.role;
