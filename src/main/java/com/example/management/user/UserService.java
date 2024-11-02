package com.example.management.user;

import com.example.management.exception.DataNotFoundException;
import com.example.management.role.Role;
import com.example.management.user.model.dto.UserRegistrationDto;
import com.example.management.user.model.dto.UserRoleAttachmentDto;
import com.example.management.user.model.entity.User;
import com.example.management.user.util.UserRoleAttachmentUtil;
import com.example.management.user.util.UserValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserValidator userValidator;
    private final UserRoleAttachmentUtil userRoleAttachmentUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    public User registerUser(UserRegistrationDto userDto){
        validateRequest(userDto);
        userDto.setPassword(passwordEncoder.encode(userDto.getPassword()));
        User user = userMapper.toEntity(userDto);

        Set<Role> roles = userRoleAttachmentUtil.validateAndRetrieveRoles(userDto.getRoleIds());
        userRoleAttachmentUtil.assignRolesToUser(user, roles);

        log.info("Adding new User: {}", user.getUsername());
        return userRepository.save(user);
    }

    public void attachRoles(UserRoleAttachmentDto userRoleAttachmentDto) {
        if (userRoleAttachmentDto.getRoleIds().isEmpty()) {
            return;
        }

        User user = findUserByUsername(userRoleAttachmentDto.getUsername());
        Set<Role> roles = userRoleAttachmentUtil.validateAndRetrieveRoles(userRoleAttachmentDto.getRoleIds());

        userRoleAttachmentUtil.assignRolesToUser(user, roles);
        userRepository.save(user);
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new DataNotFoundException("User not found"));
    }

    private void validateRequest(UserRegistrationDto userDto) {
        userValidator.validateUsername(userDto.getUsername());
        userValidator.validateEmail(userDto.getEmail());
    }
}
