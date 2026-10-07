-- Normalize only User IDs and remove columns left by the unmapped legacy User.
-- Hibernate cannot widen either side while the existing INT foreign keys remain.
DELIMITER $$
CREATE PROCEDURE normalize_user_schema_v5()
BEGIN
    DECLARE user_fks JSON;
    DECLARE fk_index INT DEFAULT 0;
    DECLARE fk_definition JSON;

    -- Refuse to alter any reference outside the User columns involved in this fix.
    IF EXISTS (
        SELECT 1 FROM information_schema.KEY_COLUMN_USAGE k
        WHERE k.REFERENCED_TABLE_SCHEMA = DATABASE() AND k.REFERENCED_TABLE_NAME = 'users'
          AND (k.TABLE_SCHEMA <> DATABASE() OR k.REFERENCED_COLUMN_NAME <> 'id'
            OR NOT ((k.TABLE_NAME IN ('enrollments', 'vocabulary_entries', 'notifications',
                                      'user_badges', 'xp_transactions') AND k.COLUMN_NAME = 'user_id')
                 OR (k.TABLE_NAME = 'users_roles' AND k.COLUMN_NAME = 'users_id'))
            OR (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE part
                WHERE part.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
                  AND part.TABLE_NAME = k.TABLE_NAME AND part.CONSTRAINT_NAME = k.CONSTRAINT_NAME) <> 1)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unexpected users foreign key; schema was not changed';
    END IF;

    IF EXISTS (SELECT 1 FROM enrollments e LEFT JOIN users u ON u.id = e.user_id WHERE u.id IS NULL)
       OR EXISTS (SELECT 1 FROM vocabulary_entries v LEFT JOIN users u ON u.id = v.user_id WHERE u.id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Orphan User reference; schema was not changed';
    END IF;

    -- Capture names and actions before dropping the constraints.
    SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(
        'table', k.TABLE_NAME, 'column', k.COLUMN_NAME, 'name', k.CONSTRAINT_NAME,
        'delete_rule', r.DELETE_RULE, 'update_rule', r.UPDATE_RULE)), JSON_ARRAY())
    INTO user_fks
    FROM information_schema.KEY_COLUMN_USAGE k
    JOIN information_schema.REFERENTIAL_CONSTRAINTS r
      ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.TABLE_NAME = k.TABLE_NAME
     AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
    WHERE k.REFERENCED_TABLE_SCHEMA = DATABASE() AND k.REFERENCED_TABLE_NAME = 'users';

    WHILE fk_index < JSON_LENGTH(user_fks) DO
        SET fk_definition = JSON_EXTRACT(user_fks, CONCAT('$[', fk_index, ']'));
        SET @user_schema_ddl = CONCAT('ALTER TABLE `',
            REPLACE(JSON_UNQUOTE(JSON_EXTRACT(fk_definition, '$.table')), '`', '``'),
            '` DROP FOREIGN KEY `',
            REPLACE(JSON_UNQUOTE(JSON_EXTRACT(fk_definition, '$.name')), '`', '``'), '`');
        PREPARE user_schema_statement FROM @user_schema_ddl;
        EXECUTE user_schema_statement;
        DEALLOCATE PREPARE user_schema_statement;
        SET fk_index = fk_index + 1;
    END WHILE;

    ALTER TABLE users MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
    ALTER TABLE enrollments MODIFY COLUMN user_id BIGINT NOT NULL;
    ALTER TABLE vocabulary_entries MODIFY COLUMN user_id BIGINT NOT NULL;
    ALTER TABLE notifications MODIFY COLUMN user_id BIGINT NOT NULL;
    ALTER TABLE user_badges MODIFY COLUMN user_id BIGINT NOT NULL;
    ALTER TABLE users_roles MODIFY COLUMN users_id BIGINT NOT NULL;
    ALTER TABLE xp_transactions MODIFY COLUMN user_id BIGINT NOT NULL;

    SET fk_index = 0;
    WHILE fk_index < JSON_LENGTH(user_fks) DO
        SET fk_definition = JSON_EXTRACT(user_fks, CONCAT('$[', fk_index, ']'));
        SET @user_schema_ddl = CONCAT('ALTER TABLE `',
            REPLACE(JSON_UNQUOTE(JSON_EXTRACT(fk_definition, '$.table')), '`', '``'),
            '` ADD CONSTRAINT `',
            REPLACE(JSON_UNQUOTE(JSON_EXTRACT(fk_definition, '$.name')), '`', '``'),
            '` FOREIGN KEY (`',
            REPLACE(JSON_UNQUOTE(JSON_EXTRACT(fk_definition, '$.column')), '`', '``'),
            '`) REFERENCES users (id) ON DELETE ',
            JSON_UNQUOTE(JSON_EXTRACT(fk_definition, '$.delete_rule')), ' ON UPDATE ',
            JSON_UNQUOTE(JSON_EXTRACT(fk_definition, '$.update_rule')));
        PREPARE user_schema_statement FROM @user_schema_ddl;
        EXECUTE user_schema_statement;
        DEALLOCATE PREPARE user_schema_statement;
        SET fk_index = fk_index + 1;
    END WHILE;

    -- These constraints may be absent because their previous creation failed.
    IF NOT EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollments' AND COLUMN_NAME = 'user_id'
          AND REFERENCED_TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME = 'users'
          AND REFERENCED_COLUMN_NAME = 'id') THEN
        ALTER TABLE enrollments ADD CONSTRAINT fk_enrollment_user FOREIGN KEY (user_id) REFERENCES users(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vocabulary_entries' AND COLUMN_NAME = 'user_id'
          AND REFERENCED_TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME = 'users'
          AND REFERENCED_COLUMN_NAME = 'id') THEN
        ALTER TABLE vocabulary_entries ADD CONSTRAINT fk_vocabulary_user FOREIGN KEY (user_id) REFERENCES users(id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'enabled') THEN
        ALTER TABLE users DROP COLUMN enabled;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'role') THEN
        ALTER TABLE users DROP COLUMN role;
    END IF;
END$$
CALL normalize_user_schema_v5()$$
DROP PROCEDURE normalize_user_schema_v5$$
DELIMITER ;
