-- Every password-reset request that would email an OTP, so a user gets at most a few OTPs per hour (each OTP allows a few
-- guesses, so this caps the guesses per hour). Rows older than the window are deleted on the user's next request.
-- Deleting the user deletes their rows.
CREATE TABLE password_reset_requests (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_password_reset_requests_user (user_id, requested_at),
    CONSTRAINT fk_password_reset_requests_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
