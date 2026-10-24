-- User types: MANAGEMENT users use the management API and may hold roles and direct permissions; REGULAR users only log in
-- and manage their own password. The type is set at creation and never changes. Root users are MANAGEMENT users.

ALTER TABLE users ADD COLUMN user_type VARCHAR(20) NOT NULL DEFAULT 'REGULAR' AFTER email;
UPDATE users SET user_type = 'MANAGEMENT'
WHERE is_root_user OR id IN (SELECT user_id FROM user_roles);
ALTER TABLE users ALTER COLUMN user_type DROP DEFAULT;

-- Permissions granted to a management user directly, next to the ones from roles.
CREATE TABLE user_permissions (
    user_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, permission_id),
    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
);

-- Creating a management user can lead to new admins, so it needs its own key (ADMIN template only).
INSERT INTO permissions (name, scope, description) VALUES
    ('users:create-management', 'TENANT', 'Create management users (users:create covers regular users)');

INSERT INTO role_template_permissions (role_template_id, permission_id)
SELECT t.id, p.id
FROM role_templates t
JOIN permissions p ON p.name = 'users:create-management'
WHERE t.name = 'ADMIN';
