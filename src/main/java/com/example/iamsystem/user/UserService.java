package com.example.iamsystem.user;

import com.example.iamsystem.exception.DataNotFoundException;
import com.example.iamsystem.exception.NoAccessException;
import com.example.iamsystem.permission.PermissionAction;
import com.example.iamsystem.role.Role;
import com.example.iamsystem.security.user.UserDetailsImpl;
import com.example.iamsystem.user.model.dto.UserDto;
import com.example.iamsystem.user.model.dto.UserRegistrationDto;
import com.example.iamsystem.user.model.dto.UserRoleAttachmentDto;
import com.example.iamsystem.user.model.entity.User;
import com.example.iamsystem.user.util.UserRoleAttachmentUtil;
import com.example.iamsystem.user.util.UserValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.parameters.P;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.example.iamsystem.constant.ErrorMessage.CREATE_IAM_USER_NO_PERMISSION;
import static com.example.iamsystem.constant.ErrorMessage.CREATE_ROOT_USER_NO_PERMISSION;
import static com.example.iamsystem.constant.ErrorMessage.UPDATE_USER_NO_PERMISSION;
import static com.example.iamsystem.constant.ErrorMessage.USER_DELETE_NO_PERMISSION;
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
    private static final String USER_CREATE_PERMISSION = "IAM:WRITE";
    private static final String USER_UPDATE_PERMISSION = "IAM:UPDATE";

    public User registerUser(UserRegistrationDto userDto) {
        validateRequest(userDto);
        validateUserCreationPermission(userDto.isRootUser());
        userDto.setPassword(passwordEncoder.encode(userDto.getPassword()));
        User user = userMapper.toEntity(userDto);
        if (!userDto.isRootUser()) {
            user.setCreatedBy(getCurrentUser());
        }

        log.info("Adding new User: {}", user.getUsername());
        return userRepository.save(user);
    }

    public void assignRoles(UserRoleAttachmentDto userRoleAttachmentDto) {
        User user = getUserByUsername(userRoleAttachmentDto.getUsername());
        validateUserUpdatePermission(user);
        Set<Role> roles = userRoleAttachmentUtil.validateAndRetrieveRoles(userRoleAttachmentDto.getRoleIds());

        userRoleAttachmentUtil.assignRolesToUser(user, roles);
        userRepository.save(user);
    }

    public void removeRoles(UserRoleAttachmentDto userRoleAttachmentDto) {
        User user = getUserByUsername(userRoleAttachmentDto.getUsername());
        validateUserUpdatePermission(user);
        Set<Role> roles = userRoleAttachmentUtil.validateAndRetrieveRoles(userRoleAttachmentDto.getRoleIds());

        userRoleAttachmentUtil.removeRolesFromUser(user, roles);
        userRepository.save(user);
    }

    public List<UserDto> findAllUsers() {
        List<User> users = userRepository.findAll();

        User currentUser = getCurrentUser();
        List<User> filteredUsers = users.stream()
                .filter(user -> isUserInTree(currentUser, user))
                .toList();

        return userMapper.toDtoList(filteredUsers);
    }

    public UserDto findUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
        validateUserFetchPermission(user);
        return userMapper.toDto(user);
    }

    public UserDto findUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
        validateUserFetchPermission(user);
        return userMapper.toDto(user);
    }

    public UserDto findUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
        validateUserFetchPermission(user);
        return userMapper.toDto(user);
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
        validateIfHasDeletePermission(user);
        userRepository.deleteById(id);
    }

    private void validateUserCreationPermission(boolean isRootUser) {
        User currentUser = getCurrentUser();
        if (isRootUser) {
            validateRootUserCreationPermission(currentUser);
        } else {
            validateNonRootUserCreationPermission(currentUser);
        }
    }

    private void validateNonRootUserCreationPermission(User currentUser) {
        if (Objects.isNull(currentUser)) { // user not logged in
            throw new NoAccessException(CREATE_IAM_USER_NO_PERMISSION);
        }

        boolean hasCreateUserPermission = currentUser.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> String.format("%s:%s", permission.getServiceName(), permission.getAction()).equals(USER_CREATE_PERMISSION));

        if (!hasCreateUserPermission) { // user doesn't have user creation permission
            throw new NoAccessException(CREATE_IAM_USER_NO_PERMISSION);
        }
    }

    private void validateUserUpdatePermission(User user) {
        User currentUser = getCurrentUser();
        assert currentUser != null;

        if (!isUserInTree(currentUser, user)) {
            throw new NoAccessException(UPDATE_USER_NO_PERMISSION);
        }
        boolean hasCreateUserPermission = currentUser.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> String.format("%s:%s", permission.getServiceName(), permission.getAction()).equals(USER_UPDATE_PERMISSION));

        if (!hasCreateUserPermission) { // user doesn't have user creation permission
            throw new NoAccessException(UPDATE_USER_NO_PERMISSION);
        }
    }

    private void validateUserFetchPermission(User user) {
        User currentUser = getCurrentUser();
        if (!isUserInTree(currentUser, user)) {
            throw new DataNotFoundException(USER_NOT_FOUND);
        }
    }

    private void validateRootUserCreationPermission(User currentUser) {
        if (Objects.nonNull(currentUser)) {
            throw new NoAccessException(CREATE_ROOT_USER_NO_PERMISSION);
        }
    }

    private void validateIfHasDeletePermission(User userToDelete) {
        User currentUser = getCurrentUser();
        if (userToDelete.isRootUser()) {
            if (!userToDelete.equals(currentUser)) { // Root user can be deleted by self only
                throw new NoAccessException(USER_DELETE_NO_PERMISSION);
            }
        } else {
            if (!isUserInTree(currentUser, userToDelete)) {
                throw new NoAccessException(USER_DELETE_NO_PERMISSION);
            }
        }
    }

    private boolean isUserInTree(User rootUser, User targetUser) {
        User currentUser = targetUser;
        while (currentUser != null) {
            if (currentUser.equals(rootUser)) {
                return true;
            }
            currentUser = currentUser.getCreatedBy();
        }
        return false;
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getUser();
        }
        return null;
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
