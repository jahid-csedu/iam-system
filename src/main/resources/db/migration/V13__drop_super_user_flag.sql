-- The platform tenant (step 10) replaced the never-used is_super_user flag: super users are the platform tenant's users.

ALTER TABLE users DROP COLUMN is_super_user;
