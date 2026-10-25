-- Deleting a user deletes their pending password-reset OTPs, like their sessions (deleting such a user failed before).
ALTER TABLE password_reset_otps DROP FOREIGN KEY fk_password_reset_otps_user;
ALTER TABLE password_reset_otps
    ADD CONSTRAINT fk_password_reset_otps_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
