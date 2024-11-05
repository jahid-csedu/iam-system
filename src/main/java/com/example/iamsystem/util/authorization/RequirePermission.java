package com.example.iamsystem.util.authorization;

import com.example.iamsystem.permission.PermissionAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String serviceName();
    PermissionAction action();
}
