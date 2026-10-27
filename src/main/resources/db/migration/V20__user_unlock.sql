-- A login lockout (too many wrong passwords) can be ended at once by another user instead of waiting for it to expire.
-- New keys reach new tenants through the templates only; existing tenants' roles get them when an admin adds them.

INSERT INTO permissions (name, scope, description) VALUES
    ('users:unlock', 'TENANT', 'End a user''s login lockout at once');

INSERT INTO role_template_permissions (role_template_id, permission_id)
SELECT t.id, p.id
FROM role_templates t
JOIN permissions p ON p.name = 'users:unlock'
WHERE t.name IN ('ADMIN', 'USER_MANAGER');
