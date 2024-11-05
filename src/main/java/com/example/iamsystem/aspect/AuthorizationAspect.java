package com.example.iamsystem.aspect;

import com.example.iamsystem.exception.NoAccessException;
import com.example.iamsystem.user.UserService;
import com.example.iamsystem.util.authorization.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Aspect
@Component
@RequiredArgsConstructor
public class AuthorizationAspect {

    private final UserService authorizationService;

    @Before("@annotation(requirePermission)")
    public void checkPermission(JoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        AtomicReference<Boolean> access = new AtomicReference<>(false);
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userDetails.getAuthorities().forEach(authority -> {
            if (authority.getAuthority().equals(requirePermission.serviceName() + ":" + requirePermission.action())) {
                access.set(true);
            }
        });

        if (Boolean.FALSE.equals(access.get())) {
            throw new NoAccessException("User does not have permission to perform this action");
        }
    }
}
