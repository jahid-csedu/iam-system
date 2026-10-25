-- Clients are ACTIVE or SUSPENDED. A suspended client refuses logins, refreshes and password resets through it, and the
-- access tokens of its sessions stop working (here and through introspection) until it is activated again.
-- The management console is never suspended.

ALTER TABLE clients ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER type;
ALTER TABLE clients ALTER COLUMN status DROP DEFAULT;

INSERT INTO permissions (name, scope, description) VALUES
    ('clients:suspend',  'TENANT', 'Suspend an application client: nobody can log in or use tokens through it'),
    ('clients:activate', 'TENANT', 'Reactivate a suspended application client');

INSERT INTO role_template_permissions (role_template_id, permission_id)
SELECT t.id, p.id
FROM role_templates t
JOIN permissions p ON p.name IN ('clients:suspend', 'clients:activate')
WHERE t.name = 'ADMIN';
