package com.example.iamsystem.user.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reads and deletes users, limited to what the current user is allowed to see.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public List<UserDto> findAllUsers() {
        User actor = currentUserProvider.requireCurrentUser();
        List<User> visibleUsers = userRepository.findAll().stream()
                .filter(user -> accessPolicy.canView(actor, user))
                .toList();
        return userMapper.toDtoList(visibleUsers);
    }

    @Transactional(readOnly = true)
    public UserDto findUserById(Long id) {
        return toVisibleDto(userRepository.getRequiredById(id));
    }

    @Transactional(readOnly = true)
    public UserDto findUserByUsername(String username) {
        return toVisibleDto(userRepository.getRequiredByUsername(username));
    }

    @Transactional(readOnly = true)
    public UserDto findUserByEmail(String email) {
        return toVisibleDto(userRepository.getRequiredByEmail(email));
    }

    @Auditable(
            value = AuditEventType.USER_DELETE,
            target = "#id"
    )
    @Transactional
    public void deleteUser(Long id) {
        User actor = currentUserProvider.requireCurrentUser();
        User user = userRepository.getRequiredById(id);
        accessPolicy.checkCanDelete(actor, user);
        userRepository.delete(user);
    }

    private UserDto toVisibleDto(User user) {
        accessPolicy.checkCanView(currentUserProvider.requireCurrentUser(), user);
        return userMapper.toDto(user);
    }
}
