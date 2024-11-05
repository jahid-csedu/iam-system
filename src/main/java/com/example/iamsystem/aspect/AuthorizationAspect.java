package com.example.iamsystem.aspect;

import com.example.iamsystem.exception.NoAccessException;
import com.example.iamsystem.permission.PermissionAction;
import com.example.iamsystem.user.UserService;
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

    private final UserService userService;

    @Before("@annotation(requirePermission)")
    public void checkPermission(JoinPoint joinPoint, RequirePermission requirePermission) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!userService.hasPermission(userDetails, requirePermission.serviceName(), requirePermission.action())) {
            throw new NoAccessException("User does not have permission to perform this action");
        }
    }
}
