-- The hourly limit counts the one-time codes of every purpose (password reset now, email verification next) in one
-- table, each purpose against its own limit.

RENAME TABLE password_reset_requests TO otp_requests;

ALTER TABLE otp_requests ADD COLUMN purpose VARCHAR(30) NOT NULL DEFAULT 'PASSWORD_RESET' AFTER user_id;
ALTER TABLE otp_requests ALTER COLUMN purpose DROP DEFAULT;

ALTER TABLE otp_requests DROP FOREIGN KEY fk_password_reset_requests_user;
ALTER TABLE otp_requests DROP INDEX idx_password_reset_requests_user;
ALTER TABLE otp_requests ADD INDEX idx_otp_requests_user (user_id, purpose, requested_at);
ALTER TABLE otp_requests
    ADD CONSTRAINT fk_otp_requests_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
