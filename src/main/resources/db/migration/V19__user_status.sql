-- Users can be deactivated and activated again. A deactivated user can't log in or refresh, and the access tokens of
-- their sessions stop working (here and through introspection) until they are activated; sessions are kept.
-- New keys reach new tenants through the templates only; existing tenants' roles get them when an admin adds them.

INSERT INTO permissions (name, scope, description) VALUES
    ('users:deactivate', 'TENANT', 'Deactivate a user: they can''t log in, and their tokens stop working'),
    ('users:activate',   'TENANT', 'Reactivate a deactivated user');

INSERT INTO role_template_permissions (role_template_id, permission_id)
SELECT t.id, p.id
FROM role_templates t
JOIN permissions p ON p.name IN ('users:deactivate', 'users:activate')
WHERE t.name IN ('ADMIN', 'USER_MANAGER');
