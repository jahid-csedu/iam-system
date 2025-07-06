-- =================================================================
-- 01_schema.sql: DDL Script for Complete Database Schema
-- =================================================================

-- Drop tables in reverse order of creation to handle foreign key constraints
DROP TABLE IF EXISTS `endpoint_permissions`;
DROP TABLE IF EXISTS `role_permissions`;
DROP TABLE IF EXISTS `user_roles`;
DROP TABLE IF EXISTS `password_reset_otp`;
DROP TABLE IF EXISTS `permissions`;
DROP TABLE IF EXISTS `applications`;
DROP TABLE IF EXISTS `services`;
DROP TABLE IF EXISTS `roles`;
DROP TABLE IF EXISTS `users`;
DROP TABLE IF EXISTS `organizations`;

-- Create new top-level tables first
CREATE TABLE `organizations` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(255) NOT NULL UNIQUE,
    `status` VARCHAR(50),
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE `services` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `service_name` VARCHAR(255) NOT NULL UNIQUE,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP
);

-- Create tables with dependencies
CREATE TABLE `users` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(255) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `full_name` VARCHAR(255),
    `email` VARCHAR(255) NOT NULL UNIQUE,
    `is_root_user` BOOLEAN NOT NULL DEFAULT false,
    `is_super_user` BOOLEAN NOT NULL DEFAULT false,
    `active` BOOLEAN NOT NULL DEFAULT true,
    `password_expired` BOOLEAN NOT NULL DEFAULT false,
    `password_expiry_date` TIMESTAMP NULL,
    `user_locked` BOOLEAN NOT NULL DEFAULT false,
    `failed_login_attempts` INT DEFAULT 0,
    `account_locked_until` TIMESTAMP NULL,
    `organization_id` BIGINT NULL,
    `created_by` BIGINT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    `version` INT,
    CONSTRAINT `fk_users_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`),
    CONSTRAINT `fk_users_created_by` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`)
);

CREATE TABLE `roles` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(255) NOT NULL,
    `description` VARCHAR(255),
    `is_global` BOOLEAN NOT NULL DEFAULT false,
    `organization_id` BIGINT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_roles_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`)
);

CREATE TABLE `applications` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `application_name` VARCHAR(255) NOT NULL,
    `organization_id` BIGINT NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_applications_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`)
);

CREATE TABLE `permissions` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `serviceName` VARCHAR(255), -- Kept for backward compatibility during migration
    `action` VARCHAR(255) NOT NULL,
    `description` VARCHAR(255),
    `service_id` BIGINT NULL,
    `application_id` BIGINT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_permissions_service` FOREIGN KEY (`service_id`) REFERENCES `services`(`id`),
    CONSTRAINT `fk_permissions_application` FOREIGN KEY (`application_id`) REFERENCES `applications`(`id`)
);

CREATE TABLE `password_reset_otp` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `otp` VARCHAR(255) NOT NULL,
    `expiry_date` TIMESTAMP NOT NULL,
    `user_id` BIGINT NOT NULL,
    CONSTRAINT `fk_otp_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
);

-- Create join tables last
CREATE TABLE `user_roles` (
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    CONSTRAINT `fk_userroles_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_userroles_role` FOREIGN KEY (`role_id`) REFERENCES `roles`(`id`) ON DELETE CASCADE
);

CREATE TABLE `role_permissions` (
    `role_id` BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    PRIMARY KEY (`role_id`, `permission_id`),
    CONSTRAINT `fk_rolepermissions_role` FOREIGN KEY (`role_id`) REFERENCES `roles`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_rolepermissions_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions`(`id`) ON DELETE CASCADE
);

CREATE TABLE `endpoint_permissions` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `http_method` VARCHAR(10) NOT NULL,
    `uri_pattern` VARCHAR(255) NOT NULL,
    `permission_id` BIGINT NOT NULL,
    CONSTRAINT `fk_endpointpermissions_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions`(`id`) ON DELETE CASCADE
);
