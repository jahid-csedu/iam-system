-- Permissions become a fixed catalog of <resource>:<action> keys, added only by migrations together with the feature.
-- TENANT keys are used inside a tenant; PLATFORM keys (step 10) by platform super users only.
-- The free-text permissions created through the API (development data only) and their role links are removed.

DELETE FROM role_permissions;
DELETE FROM permissions;

ALTER TABLE permissions DROP FOREIGN KEY fk_permissions_service;
ALTER TABLE permissions DROP FOREIGN KEY fk_permissions_application;
ALTER TABLE permissions
    DROP COLUMN service_id,
    DROP COLUMN application_id,
    DROP COLUMN service_name,
    DROP COLUMN action,
    DROP COLUMN created_at,
    DROP COLUMN updated_at,
    ADD COLUMN name VARCHAR(100) NOT NULL AFTER id,
    ADD COLUMN scope VARCHAR(20) NOT NULL AFTER name,
    MODIFY description VARCHAR(255) NOT NULL,
    ADD CONSTRAINT uk_permissions_name UNIQUE (name);

-- Registry of other systems' permissions: the IAM manages only its own permissions.
DROP TABLE services;

INSERT INTO permissions (name, scope, description) VALUES
    ('users:read',           'TENANT', 'List and view the users of the tenant'),
    ('users:create',         'TENANT', 'Create users in the tenant'),
    ('users:update',         'TENANT', 'Assign roles to and remove roles from other users'),
    ('users:delete',         'TENANT', 'Delete users'),
    ('users:reset-password', 'TENANT', 'Reset another user''s password (the new password is emailed to the user)'),
    ('roles:read',           'TENANT', 'List and view the roles of the tenant'),
    ('roles:create',         'TENANT', 'Create roles'),
    ('roles:update',         'TENANT', 'Rename roles and change their permissions'),
    ('roles:delete',         'TENANT', 'Delete roles'),
    ('permissions:read',     'TENANT', 'List and view the permission catalog');
