-- =================================================================
-- 02_data.sql: DML Script for Seeding Dummy Data
-- =================================================================

-- Use variables for clarity
SET @hashed_password = '$2a$10$m1EyheAX0f7PXI.doNo/TOQeJw6erVYeauw45ZPhdab8Q6mix4hNu'; -- Placeholder for a Bcrypt hash of 'Password123!' (meets policy)

-- =================================================================
-- 1. Global Space Data
-- =================================================================

-- Create Global Services
INSERT INTO `services` (`id`, `service_name`) VALUES
(1, 'iam-system-api'),
(2, 'global-billing-api');

-- Create Permissions for Global Services
INSERT INTO `permissions` (`id`, `action`, `description`, `service_id`) VALUES
(1, 'VIEW_SYSTEM_HEALTH', 'Can view system-wide health checks', 1),
(2, 'MANAGE_BILLING', 'Can manage billing for all organizations', 2);

-- Create a Global Role
INSERT INTO `roles` (`id`, `name`, `description`, `is_global`) VALUES
(1, 'Global Billing Manager', 'Manages billing across all organizations', true);

-- Assign permissions to the Global Role
INSERT INTO `role_permissions` (`role_id`, `permission_id`) VALUES
(1, 2); -- Global Billing Manager gets MANAGE_BILLING

-- =================================================================
-- 2. Users (Super User, Global Admin, and Tenant Users)
-- =================================================================

-- Create a Super User (no organization)
INSERT INTO `users` (`id`, `username`, `password`, `full_name`, `email`, `is_super_user`, `organization_id`, `version`) VALUES
(1, 'super_admin', @hashed_password, 'Super Admin', 'super.admin@example.com', true, NULL, 0);

-- Create a user who will be a Global Admin (but belongs to an org)
INSERT INTO `users` (`id`, `username`, `password`, `full_name`, `email`, `organization_id`, `version`) VALUES
(2, 'billing_admin', @hashed_password, 'Billing Admin', 'billing.admin@example.com', NULL, 0); -- Org to be assigned later

-- Assign the Global Role to the Global Admin
INSERT INTO `user_roles` (`user_id`, `role_id`) VALUES
(2, 1); -- billing_admin gets Global Billing Manager role

-- =================================================================
-- 3. Tenant Space: Primary Organization
-- =================================================================

-- Create the organization
INSERT INTO `organizations` (`id`, `name`, `status`) VALUES
(1, 'Primary Organization', 'ACTIVE');

-- Assign the billing_admin to this organization
UPDATE `users` SET `organization_id` = 1 WHERE `id` = 2;

-- Create a Root User for the organization
INSERT INTO `users` (`id`, `username`, `password`, `full_name`, `email`, `is_root_user`, `organization_id`, `version`) VALUES
(3, 'primary_root', @hashed_password, 'Primary Root User', 'primary.root@example.com', true, 1, 0);

-- Create a regular user for the organization
INSERT INTO `users` (`id`, `username`, `password`, `full_name`, `email`, `organization_id`, `version`) VALUES
(4, 'primary_user', @hashed_password, 'Primary Regular User', 'primary.user@example.com', 1, 0);

-- Create an Application specific to this organization
INSERT INTO `applications` (`id`, `application_name`, `organization_id`) VALUES
(1, 'Primary E-commerce Site', 1);

-- Create Permissions for this application
INSERT INTO `permissions` (`id`, `action`, `description`, `application_id`) VALUES
(10, 'VIEW_PRODUCTS', 'Can view products', 1),
(11, 'ADD_TO_CART', 'Can add products to cart', 1);

-- Create a Role specific to this organization
INSERT INTO `roles` (`id`, `name`, `description`, `organization_id`) VALUES
(10, 'Product Viewer', 'Can view products on the e-commerce site', 1);

-- Assign permissions to the organizational role
INSERT INTO `role_permissions` (`role_id`, `permission_id`) VALUES
(10, 10); -- Product Viewer gets VIEW_PRODUCTS

-- Assign the organizational role to the regular user
INSERT INTO `user_roles` (`user_id`, `role_id`) VALUES
(4, 10); -- primary_user gets Product Viewer role

-- =================================================================
-- 4. Tenant Space: Second Organization
-- =================================================================

-- Create the organization
INSERT INTO `organizations` (`id`, `name`, `status`) VALUES
(2, 'Second Organization', 'ACTIVE');

-- Create a Root User for the organization
INSERT INTO `users` (`id`, `username`, `password`, `full_name`, `email`, `is_root_user`, `organization_id`, `version`) VALUES
(5, 'second_root', @hashed_password, 'Second Root User', 'second.root@example.com', true, 2, 0);

-- Create an Application specific to this organization
INSERT INTO `applications` (`id`, `application_name`, `organization_id`) VALUES
(2, 'Secondary CRM', 2);

-- Create Permissions for this application
INSERT INTO `permissions` (`id`, `action`, `description`, `application_id`) VALUES
(20, 'VIEW_CUSTOMERS', 'Can view customers in CRM', 2),
(21, 'EDIT_CUSTOMER', 'Can edit customer details in CRM', 2);

-- =================================================================
-- 5. Endpoint Mappings
-- =================================================================
INSERT INTO `endpoint_permissions` (`http_method`, `uri_pattern`, `permission_id`) VALUES
('GET', '/api/billing/summary', 2),       -- Requires MANAGE_BILLING
('GET', '/api/products', 10),             -- Requires VIEW_PRODUCTS
('POST', '/api/cart', 11),                -- Requires ADD_TO_CART
('GET', '/api/customers', 20);            -- Requires VIEW_CUSTOMERS
