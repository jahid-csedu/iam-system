package com.example.iamsystem.user.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and deletes users of the current user's tenant.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final Sort BY_ID = Sort.by("id");

    private final UserRepository userRepository;
    private final UserAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final UserMapper userMapper;

    /**
     * One page of the tenant's users, ordered by id. Page sizes above {@link #MAX_PAGE_SIZE} are capped;
     * a negative page or a size below 1 is rejected.
     */
    @Transactional(readOnly = true)
    public Page<UserDto> findUsers(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), BY_ID);
        return userRepository.findAllByTenantId(currentUserProvider.requireCurrentTenantId(), pageRequest)
                .map(userMapper::toDto);
    }

    @Transactional(readOnly = true)
    public UserDto findUserById(Long id) {
        return userMapper.toDto(userRepository.getRequiredByIdAndTenantId(id, currentUserProvider.requireCurrentTenantId()));
    }

    @Transactional(readOnly = true)
    public UserDto findUserByUsername(String username) {
        return userMapper.toDto(userRepository.getRequiredByUsernameAndTenantId(username, currentUserProvider.requireCurrentTenantId()));
    }

    @Transactional(readOnly = true)
    public UserDto findUserByEmail(String email) {
        return userMapper.toDto(userRepository.getRequiredByEmailAndTenantId(email, currentUserProvider.requireCurrentTenantId()));
    }

    @Auditable(
            value = AuditEventType.USER_DELETE,
            target = "#id"
    )
    @Transactional
    public void deleteUser(Long id) {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        User user = userRepository.getRequiredByIdAndTenantId(id, actor.tenantId());
        accessPolicy.checkCanDelete(actor, user);
        userRepository.delete(user);
    }
}
