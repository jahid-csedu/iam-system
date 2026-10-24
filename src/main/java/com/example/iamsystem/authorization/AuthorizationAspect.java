package com.example.iamsystem.authorization;

import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * Enforces {@link RequirePermission} on controller methods.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuthorizationAspect {

    private final AuthorizationService authorizationService;
    private final CurrentUserProvider currentUserProvider;

    @Before("@annotation(requirePermission)")
    public void checkPermission(RequirePermission requirePermission) {
        authorizationService.checkPermission(currentUserProvider.requireCurrentActor(), requirePermission.value());
    }
}
