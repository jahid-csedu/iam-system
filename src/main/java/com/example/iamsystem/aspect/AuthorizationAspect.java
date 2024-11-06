package com.example.iamsystem.aspect;

import com.example.iamsystem.exception.NoAccessException;
import com.example.iamsystem.permission.PermissionAction;
import com.example.iamsystem.util.authorization.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class AuthorizationAspect {

    @Before("@annotation(requirePermission)")
    public void checkPermission(JoinPoint joinPoint, RequirePermission requirePermission) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!hasPermission(userDetails, requirePermission.serviceName(), requirePermission.action())) {
            throw new NoAccessException("User does not have permission to perform this action");
        }
    }

    private static boolean hasPermission(UserDetails userDetails, String serviceName, PermissionAction action) {
        return userDetails.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(serviceName + ":" + action));
    }
}
