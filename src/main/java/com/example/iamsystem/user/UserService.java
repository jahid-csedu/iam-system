package com.example.iamsystem.user;

import com.example.iamsystem.exception.DataNotFoundException;
import com.example.iamsystem.permission.PermissionAction;
import com.example.iamsystem.role.Role;
import com.example.iamsystem.user.model.dto.UserDto;
import com.example.iamsystem.user.model.dto.UserRegistrationDto;
import com.example.iamsystem.user.model.dto.UserRoleAttachmentDto;
import com.example.iamsystem.user.model.entity.User;
import com.example.iamsystem.user.util.UserRoleAttachmentUtil;
import com.example.iamsystem.user.util.UserValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static com.example.iamsystem.constant.ErrorMessage.USER_NOT_FOUND;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserValidator userValidator;
    private final UserRoleAttachmentUtil userRoleAttachmentUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    public User registerUser(UserRegistrationDto userDto) {
        validateRequest(userDto);
        userDto.setPassword(passwordEncoder.encode(userDto.getPassword()));
        User user = userMapper.toEntity(userDto);

        log.info("Adding new User: {}", user.getUsername());
        return userRepository.save(user);
    }

    public void assignRoles(UserRoleAttachmentDto userRoleAttachmentDto) {
        User user = getUserByUsername(userRoleAttachmentDto.getUsername());
        Set<Role> roles = userRoleAttachmentUtil.validateAndRetrieveRoles(userRoleAttachmentDto.getRoleIds());

        userRoleAttachmentUtil.assignRolesToUser(user, roles);
        userRepository.save(user);
    }

    public void removeRoles(UserRoleAttachmentDto userRoleAttachmentDto) {
        User user = getUserByUsername(userRoleAttachmentDto.getUsername());
        Set<Role> roles = userRoleAttachmentUtil.validateAndRetrieveRoles(userRoleAttachmentDto.getRoleIds());

        userRoleAttachmentUtil.removeRolesFromUser(user, roles);
        userRepository.save(user);
    }

    public List<UserDto> findAllUsers() {
        return userMapper.toDtoList(userRepository.findAll());
    }

    public UserDto findUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
        return userMapper.toDto(user);
    }

    public UserDto findUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
        return userMapper.toDto(user);
    }

    public UserDto findUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
        return userMapper.toDto(user);
    }

    public void deleteUser(Long id) {
        this.findUserById(id);
        userRepository.deleteById(id);
    }

    public boolean hasPermission(UserDetails userDetails, String serviceName, PermissionAction action) {
        return userDetails.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(serviceName + ":" + action));
    }

    private void validateRequest(UserRegistrationDto userDto) {
        userValidator.validateUsernameAvailable(userDto.getUsername());
        userValidator.validateEmailAvailable(userDto.getEmail());
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
    }
}
