-- PLATFORM permissions: what super users may do to tenants. They are never part of a tenant's roles, so no role template
-- gets them; the platform root holds them implicitly, and other platform users will receive them through grants.

INSERT INTO permissions (name, scope, description) VALUES
    ('tenants:read',     'PLATFORM', 'List and view all tenants'),
    ('tenants:create',   'PLATFORM', 'Create tenants with their root user'),
    ('tenants:suspend',  'PLATFORM', 'Suspend a tenant: none of its users can log in or use their tokens'),
    ('tenants:activate', 'PLATFORM', 'Reactivate a suspended tenant');
