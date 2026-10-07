-- Run this once against an existing MySQL database that was created with users.id INT.
-- Hibernate cannot safely alter these columns while foreign keys still exist.

DROP PROCEDURE IF EXISTS drop_fk_to_users;
DROP PROCEDURE IF EXISTS alter_column_if_exists;
DROP PROCEDURE IF EXISTS add_fk_if_missing;

DELIMITER //

CREATE PROCEDURE drop_fk_to_users(IN p_table_name VARCHAR(64), IN p_column_name VARCHAR(64))
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE fk_name VARCHAR(64);
    DECLARE fk_cursor CURSOR FOR
        SELECT constraint_name
        FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
          AND referenced_table_name = 'users'
          AND referenced_column_name = 'id';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN fk_cursor;
    read_loop: LOOP
        FETCH fk_cursor INTO fk_name;
        IF done THEN
            LEAVE read_loop;
        END IF;

        SET @drop_sql = CONCAT('ALTER TABLE `', p_table_name, '` DROP FOREIGN KEY `', fk_name, '`');
        PREPARE stmt FROM @drop_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE fk_cursor;
END//

CREATE PROCEDURE alter_column_if_exists(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_definition VARCHAR(255)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @alter_sql = CONCAT(
            'ALTER TABLE `', p_table_name, '` MODIFY COLUMN `', p_column_name, '` ', p_definition
        );
        PREPARE stmt FROM @alter_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

CREATE PROCEDURE add_fk_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_constraint_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_delete_rule VARCHAR(32)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND constraint_name = p_constraint_name
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        SET @fk_sql = CONCAT(
            'ALTER TABLE `', p_table_name, '` ADD CONSTRAINT `', p_constraint_name,
            '` FOREIGN KEY (`', p_column_name, '`) REFERENCES `users`(`id`)',
            p_delete_rule
        );
        PREPARE stmt FROM @fk_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DELIMITER ;

CALL drop_fk_to_users('enrollments', 'user_id');
CALL drop_fk_to_users('vocabulary_entries', 'user_id');
CALL drop_fk_to_users('user_badges', 'user_id');
CALL drop_fk_to_users('xp_transactions', 'user_id');
CALL drop_fk_to_users('notifications', 'user_id');
CALL drop_fk_to_users('users_roles', 'users_id');

CALL alter_column_if_exists('users', 'id', 'BIGINT NOT NULL AUTO_INCREMENT');
CALL alter_column_if_exists('enrollments', 'user_id', 'BIGINT NOT NULL');
CALL alter_column_if_exists('vocabulary_entries', 'user_id', 'BIGINT NOT NULL');
CALL alter_column_if_exists('user_badges', 'user_id', 'BIGINT NOT NULL');
CALL alter_column_if_exists('xp_transactions', 'user_id', 'BIGINT NOT NULL');
CALL alter_column_if_exists('notifications', 'user_id', 'BIGINT NOT NULL');
CALL alter_column_if_exists('users_roles', 'users_id', 'BIGINT NOT NULL');

CALL add_fk_if_missing('enrollments', 'fk_enrollment_user', 'user_id', '');
CALL add_fk_if_missing('vocabulary_entries', 'fk_vocabulary_user', 'user_id', '');
CALL add_fk_if_missing('user_badges', 'fk_user_badge_user', 'user_id', ' ON DELETE CASCADE');
CALL add_fk_if_missing('xp_transactions', 'fk_xp_user', 'user_id', ' ON DELETE CASCADE');
CALL add_fk_if_missing('notifications', 'fk_notification_user', 'user_id', ' ON DELETE CASCADE');
CALL add_fk_if_missing('users_roles', 'fk_users_roles_user', 'users_id', ' ON DELETE CASCADE');

DROP PROCEDURE IF EXISTS add_fk_if_missing;
DROP PROCEDURE IF EXISTS alter_column_if_exists;
DROP PROCEDURE IF EXISTS drop_fk_to_users;
