-- Predefined roles: a template copied into every new tenant at signup. The copies are ordinary tenant roles that the tenant
-- may change or delete; later changes to the template never reach existing tenants.
-- A migration that adds a permission key also decides which templates get it (ADMIN always gets every key).

CREATE TABLE role_templates (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_role_templates_name UNIQUE (name)
);

CREATE TABLE role_template_permissions (
    role_template_id BIGINT NOT NULL,
    permission_id    BIGINT NOT NULL,
    PRIMARY KEY (role_template_id, permission_id),
    CONSTRAINT fk_role_template_permissions_template FOREIGN KEY (role_template_id) REFERENCES role_templates (id),
    CONSTRAINT fk_role_template_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
);

INSERT INTO role_templates (name, description) VALUES
    ('ADMIN',        'Manages the whole tenant: users, roles and password resets'),
    ('USER_MANAGER', 'Creates, updates and deletes users'),
    ('VIEWER',       'Views users, roles and the permission catalog');

INSERT INTO role_template_permissions (role_template_id, permission_id)
SELECT t.id, p.id
FROM role_templates t
JOIN permissions p ON p.scope = 'TENANT'
WHERE t.name = 'ADMIN';

INSERT INTO role_template_permissions (role_template_id, permission_id)
SELECT t.id, p.id
FROM role_templates t
JOIN permissions p ON p.name IN ('users:read', 'users:create', 'users:update', 'users:delete')
WHERE t.name = 'USER_MANAGER';

INSERT INTO role_template_permissions (role_template_id, permission_id)
SELECT t.id, p.id
FROM role_templates t
JOIN permissions p ON p.name IN ('users:read', 'roles:read', 'permissions:read')
WHERE t.name = 'VIEWER';
