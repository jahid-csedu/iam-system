delete from `user_roles`;
delete from `roles`;
update `users` set `created_by` = null;
delete from `users`;