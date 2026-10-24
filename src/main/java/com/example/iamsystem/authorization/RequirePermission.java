package com.example.iamsystem.authorization;

import com.example.iamsystem.permission.PermissionKey;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The permission an endpoint requires; enforced by {@link AuthorizationAspect}. Every protected endpoint has its own key.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    PermissionKey value();
}
