package com.example.iamsystem.user.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates users inside the current user's tenant. Root users are created only at tenant signup.
 */
@Service
@RequiredArgsConstructor
public class UserCreationService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final UserFactory userFactory;
    private final CurrentUserProvider currentUserProvider;
    private final UserMapper userMapper;

    @Auditable(
            value = AuditEventType.USER_CREATION,
            target = "#request.username()",
            detailsExpression = "T(java.util.Map).of('new_user_id', #result.id, 'tenant_id', #result.tenantId)"
    )
    @Transactional
    public UserDto createUser(UserCreationDto request) {
        User creator = currentUserProvider.requireCurrentUser();
        User user = userFactory.newUser(request, tenantRepository.getReferenceById(creator.getTenant().getId()));
        user.setCreatedBy(creator);
        return userMapper.toDto(userRepository.save(user));
    }
}
