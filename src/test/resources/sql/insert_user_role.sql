delete from `user_roles`;
delete from `roles`;
delete from `users`;

INSERT INTO
  `roles` (
    `id`,
    `created_at`,
    `description`,
    `name`,
    `updated_at`
  )
VALUES
  (
    1,
    '2025-06-27 14:10:14.550149',
    'Admin User role of the system',
    'ADMIN',
    '2025-06-27 14:10:14.550149'
  );

INSERT INTO
  `users` (
    `id`,
    `account_locked_until`,
    `active`,
    `created_at`,
    `email`,
    `failed_login_attempts`,
    `full_name`,
    `password`,
    `password_expired`,
    `is_root_user`,
    `updated_at`,
    `user_locked`,
    `username`,
    `created_by`
  )
VALUES
  (
    1,
    NULL,
    '1',
    NULL,
    'root@test.com',
    0,
    'Root User',
    '$2a$10$aPjQgDcFSRBvSY6QY2jRgOz28DM6D0Go./U/fLJNblCHlx4kwIN1O',
    '0',
    '1',
    '2025-06-29 11:16:25.108024',
    '0',
    'root',
    NULL
  );

INSERT INTO
  `users` (
    `id`,
    `account_locked_until`,
    `active`,
    `created_at`,
    `email`,
    `failed_login_attempts`,
    `full_name`,
    `password`,
    `password_expired`,
    `is_root_user`,
    `updated_at`,
    `user_locked`,
    `username`,
    `created_by`
  )
VALUES
  (
    2,
    NULL,
    '1',
    NULL,
    'user@test.com',
    0,
    'Normal User',
    '$2a$10$aPjQgDcFSRBvSY6QY2jRgOz28DM6D0Go./U/fLJNblCHlx4kwIN1O',
    '0',
    '0',
    '2025-06-29 11:16:25.108024',
    '0',
    'user',
    '1'
  );