package com.example.iamsystem.aspect;

import com.example.iamsystem.exception.NoAccessException;
import com.example.iamsystem.permission.PermissionService;
import com.example.iamsystem.security.user.UserDetailsImpl;
import com.example.iamsystem.util.authorization.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class AuthorizationAspect {
    private final PermissionService permissionService;

    @Before("@annotation(requirePermission)")
    public void checkPermission(JoinPoint joinPoint, RequirePermission requirePermission) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!permissionService.hasPermission(userDetails.getUser(), requirePermission.serviceName()+":"+requirePermission.action())) {
            throw new NoAccessException("User does not have permission to perform this action");
        }
    }
}
