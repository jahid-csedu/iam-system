delete from `user_sessions`;
delete from `password_reset_otps`;
delete from `user_roles`;
delete from `roles`;
update `users` set `created_by` = null;
delete from `users`;
delete from `clients`;
delete from `tenants`;
