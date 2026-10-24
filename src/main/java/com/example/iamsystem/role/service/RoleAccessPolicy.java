package com.example.iamsystem.role.service;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.example.iamsystem.common.exception.ErrorMessage.HELD_ROLE_CHANGE;

/**
 * Decides whether an actor may change a role of their tenant. Endpoint permissions are checked separately by
 * {@code @RequirePermission}, and the tenant boundary by the tenant-scoped lookups in {@code RoleRepository}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoleAccessPolicy {

    private final UserRepository userRepository;

    /**
     * Nobody changes or deletes a role they hold; another user with the permission has to do it.
     */
    public void checkCanModify(AuthenticatedActor actor, Role role) {
        if (userRepository.existsByIdAndRolesId(actor.userId(), role.getId())) {
            log.warn("User '{}' tried to change role '{}', which they hold", actor.username(), role.getName());
            throw new NoAccessException(HELD_ROLE_CHANGE);
        }
    }
}
