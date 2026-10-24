package com.example.iamsystem.user.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.authorization.AuthorizationService;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.iamsystem.permission.PermissionKey.USERS_CREATE_MANAGEMENT;

/**
 * Creates users inside the current user's tenant. Root users are created only at tenant signup.
 * Creating a management user needs {@code users:create-management} on top of the endpoint's {@code users:create}.
 */
@Service
@RequiredArgsConstructor
public class UserCreationService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final UserFactory userFactory;
    private final AuthorizationService authorizationService;
    private final CurrentUserProvider currentUserProvider;
    private final UserMapper userMapper;

    @Auditable(
            value = AuditEventType.USER_CREATION,
            target = "#request.username()",
            detailsExpression = "T(java.util.Map).of('new_user_id', #result.id, 'tenant_id', #result.tenantId, 'user_type', #result.userType)"
    )
    @Transactional
    public UserDto createUser(UserCreationDto request) {
        AuthenticatedActor creator = currentUserProvider.requireCurrentActor();
        UserType userType = request.userType() == null ? UserType.REGULAR : request.userType();
        if (userType == UserType.MANAGEMENT) {
            authorizationService.checkPermission(creator, USERS_CREATE_MANAGEMENT);
        }
        User user = userFactory.newUser(request, tenantRepository.getReferenceById(creator.tenantId()), userType);
        user.setCreatedBy(userRepository.getReferenceById(creator.userId()));
        return userMapper.toDto(userRepository.save(user));
    }
}
