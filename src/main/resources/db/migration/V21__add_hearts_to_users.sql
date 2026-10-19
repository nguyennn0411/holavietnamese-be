-- Add hearts column to users table for gamified learning lifecycle
ALTER TABLE users ADD COLUMN hearts INT DEFAULT 5;
UPDATE users SET hearts = 5 WHERE hearts IS NULL;
