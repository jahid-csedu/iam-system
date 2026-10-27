-- Email verification: an address is verified once the user has typed a code sent to it. Until then it doesn't receive
-- password resets of the user's own request (from step 13c-2). Rows that exist before this migration count as verified;
-- the application sets the value for every new user.

ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE AFTER email;
ALTER TABLE users ALTER COLUMN email_verified DROP DEFAULT;

-- The pending verification code of a user (at most one), as a bcrypt hash, with its wrong guesses. Deleting the user
-- deletes it.
CREATE TABLE email_verification_codes (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    code_hash       VARCHAR(255) NOT NULL,
    expires_at      DATETIME(6)  NOT NULL,
    failed_attempts INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_email_verification_codes_user UNIQUE (user_id),
    CONSTRAINT fk_email_verification_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
