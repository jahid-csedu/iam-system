package com.example.iamsystem.user.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.security.service.FailedPasswordCounter;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deactivates, activates and unlocks users of the current tenant. Never yourself and never the root user
 * ({@link UserAccessPolicy#checkCanChangeStatus}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserStatusService {

    private final UserRepository userRepository;
    private final UserAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final UserMapper userMapper;
    private final FailedPasswordCounter failedPasswordCounter;

    /**
     * The user can't log in or refresh, and the tokens of their sessions stop working until they are activated again;
     * sessions are kept. Deactivating a deactivated user changes nothing.
     */
    @Auditable(value = AuditEventType.USER_DEACTIVATE, target = "#id")
    @Transactional
    public UserDto deactivateUser(Long id) {
        return changeStatus(id, false);
    }

    /**
     * Logins work again, and so do the tokens of unexpired sessions. Activating an active user changes nothing.
     */
    @Auditable(value = AuditEventType.USER_ACTIVATE, target = "#id")
    @Transactional
    public UserDto activateUser(Long id) {
        return changeStatus(id, true);
    }

    /**
     * Ends a login lockout at once (wrong passwords counted so far are forgotten too), so the user can log in and their
     * tokens work again. Unlocking an unlocked user changes nothing.
     */
    @Auditable(value = AuditEventType.USER_UNLOCK, target = "#id")
    @Transactional
    public UserDto unlockUser(Long id) {
        User user = requireChangeableUser(id);
        failedPasswordCounter.unlock(user);
        return userMapper.toDto(user);
    }

    private UserDto changeStatus(Long id, boolean active) {
        User user = requireChangeableUser(id);
        if (user.isActive() != active) {
            user.setActive(active);
            log.info("User '{}' is now {}", user.getUsername(), active ? "active" : "deactivated");
        }
        return userMapper.toDto(user);
    }

    private User requireChangeableUser(Long id) {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        User user = userRepository.getRequiredByIdAndTenantId(id, actor.tenantId());
        accessPolicy.checkCanChangeStatus(actor, user);
        return user;
    }
}
