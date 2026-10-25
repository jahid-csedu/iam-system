-- Reset OTPs are stored only as a bcrypt hash: six digits are too few for a plain hash (all of them can be hashed in under a
-- second), while bcrypt makes trying them all take far longer than an OTP lives. Pending OTPs were stored in plain text
-- and live only minutes, so they are deleted; their users request a new one.
DELETE FROM password_reset_otps;
ALTER TABLE password_reset_otps RENAME COLUMN otp TO otp_hash;
