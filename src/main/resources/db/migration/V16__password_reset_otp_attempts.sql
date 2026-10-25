-- A reset OTP is checked against its own user only, and it is deleted after too many wrong guesses. OTPs no longer have to
-- be unique across users (two users may get the same six digits).
ALTER TABLE password_reset_otps ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0 AFTER expiry_date;
ALTER TABLE password_reset_otps ALTER COLUMN failed_attempts DROP DEFAULT;
ALTER TABLE password_reset_otps DROP INDEX uk_password_reset_otps_otp;
