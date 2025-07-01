package com.example.iamsystem.user;

import com.example.iamsystem.exception.DataNotFoundException;
import com.example.iamsystem.exception.InvalidPasswordException;
import com.example.iamsystem.exception.NoAccessException;
import com.example.iamsystem.permission.PermissionService;
import com.example.iamsystem.role.model.Role;
import com.example.iamsystem.security.user.DefaultUserDetails;
import com.example.iamsystem.user.model.dto.PasswordChangeDto;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.example.iamsystem.constant.ErrorMessage.INVALID_OLD_PASSWORD;
import static com.example.iamsystem.constant.ErrorMessage.NO_PERMISSION;
import static com.example.iamsystem.constant.ErrorMessage.USER_NOT_FOUND;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserValidator userValidator;
    private final UserRoleAttachmentUtil userRoleAttachmentUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    private static final UserMapper userMapper = Mappers.getMapper(UserMapper.class);
    private static final String USER_CREATE_PERMISSION = "IAM:WRITE";
    private static final String USER_UPDATE_PERMISSION = "IAM:UPDATE";

    public UserDto registerUser(UserRegistrationDto userDto) {
        log.debug("Attempting to register new user with username: {}", userDto.getUsername());
        validateRequest(userDto);
        userValidator.validatePasswordPolicy(userDto.getPassword());
        validateUserCreationPermission(userDto.isRootUser());
        userDto.setPassword(passwordEncoder.encode(userDto.getPassword()));
        User user = userMapper.toEntity(userDto);
        if (!userDto.isRootUser()) {
            user.setCreatedBy(getCurrentUser());
        }

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getId());
        return userMapper.toDto(savedUser);
    }

    public void changePassword(PasswordChangeDto passwordChangeDto, Optional<Long> userId) {
        log.debug("Attempting to change password. User ID from request: {}", userId.orElse(null));
        User currentUser = getCurrentUser();
        if (userId.isPresent()) {
            User userToUpdate = userRepository.findById(userId.get())
                    .orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
            validatePasswordChangePermission(currentUser, userToUpdate);
            updatePassword(userToUpdate, passwordChangeDto.newPassword());
        } else {
            validateOldPassword(currentUser, passwordChangeDto.oldPassword());
            updatePassword(currentUser, passwordChangeDto.newPassword());
        }
    }

    public void assignRoles(UserRoleAttachmentDto userRoleAttachmentDto) {
        log.debug("Attempting to assign roles to user with: {}", userRoleAttachmentDto.getUsername());
        User user = getUserByUsername(userRoleAttachmentDto.getUsername());
        validateUserUpdatePermission(user);
        Set<Role> roles = userRoleAttachmentUtil.validateAndRetrieveRoles(userRoleAttachmentDto.getRoleIds());

        userRoleAttachmentUtil.assignRolesToUser(user, roles);
        userRepository.save(user);
        log.info("Roles assigned successfully to user: {}", userRoleAttachmentDto.getUsername());
    }

    public void removeRoles(UserRoleAttachmentDto userRoleAttachmentDto) {
        log.debug("Attempting to remove roles from user with: {}", userRoleAttachmentDto.getUsername());
        User user = getUserByUsername(userRoleAttachmentDto.getUsername());
        validateUserUpdatePermission(user);
        Set<Role> roles = userRoleAttachmentUtil.validateAndRetrieveRoles(userRoleAttachmentDto.getRoleIds());

        userRoleAttachmentUtil.removeRolesFromUser(user, roles);
        userRepository.save(user);
        log.info("Roles removed successfully from user: {}", userRoleAttachmentDto.getUsername());
    }

    public List<UserDto> findAllUsers() {
        log.debug("Attempting to find all users");
        List<User> users = userRepository.findAll();

        User currentUser = getCurrentUser();
        List<User> filteredUsers = users.stream()
                .filter(user -> {
                    assert currentUser != null;
                    return isUserInTree(currentUser, user);
                })
                .toList();
        log.info("Retrieved {} users after filtering", filteredUsers.size());
        return userMapper.toDtoList(filteredUsers);
    }

    public UserDto findUserById(Long id) {
        log.debug("Attempting to find user by ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found with ID: {}", id);
                    return new DataNotFoundException(USER_NOT_FOUND);
                });
        validateUserFetchPermission(user);
        log.info("Successfully retrieved user with ID: {}", id);
        return userMapper.toDto(user);
    }

    public UserDto findUserByUsername(String username) {
        log.debug("Attempting to find user by username: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found with username: {}", username);
                    return new DataNotFoundException(USER_NOT_FOUND);
                });
        validateUserFetchPermission(user);
        log.info("Successfully retrieved user with username: {}", username);
        return userMapper.toDto(user);
    }

    public UserDto findUserByEmail(String email) {
        log.debug("Attempting to find user by email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User not found with email: {}", email);
                    return new DataNotFoundException(USER_NOT_FOUND);
                });
        validateUserFetchPermission(user);
        log.info("Successfully retrieved user with email: {}", email);
        return userMapper.toDto(user);
    }

    public void deleteUser(Long id) {
        log.debug("Attempting to delete user with ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found for deletion with ID: {}", id);
                    return new DataNotFoundException(USER_NOT_FOUND);
                });
        validateUserDeletionPermission(user);
        userRepository.deleteById(id);
        log.info("User with ID: {} deleted successfully", id);
    }

    private void validateUserCreationPermission(boolean isRootUser) {
        log.debug("Validating user creation permission for root user: {}", isRootUser);
        User currentUser = getCurrentUser();
        if (isRootUser) {
            if (Objects.nonNull(currentUser)) {
                log.warn("Attempted to create root user by another root user: {}", currentUser.getUsername());
                throw new NoAccessException(NO_PERMISSION);
            }
            return;
        }
        validateUserPermission(currentUser, USER_CREATE_PERMISSION);
        log.debug("User creation permission validated.");
    }

    private void validateUserUpdatePermission(User user) {
        log.debug("Validating user update permission for user ID: {}", user.getId());
        User currentUser = getCurrentUser();

        assert currentUser != null;
        if (!isUserInTree(currentUser, user)) {
            log.warn("Current user '{}' does not have permission to update user '{}'",currentUser.getUsername() , user.getUsername());
            throw new NoAccessException(NO_PERMISSION);
        }

        validateUserPermission(currentUser, USER_UPDATE_PERMISSION);
        log.debug("User update permission validated for user ID: {}", user.getId());
    }

    private void validateUserPermission(User user, String requiredPermission) {
        log.debug("Checking user '{}' for permission: {}", user != null ? user.getUsername() : "N/A", requiredPermission);
        if(Objects.isNull(user)) {
            log.warn("No current user found for permission check: {}", requiredPermission);
            throw new NoAccessException(NO_PERMISSION);
        }
        boolean hasPermission = permissionService.hasPermission(requiredPermission);
        if (!hasPermission) {
            log.warn("User '{}' does NOT have required permission: {}", user.getUsername(), requiredPermission);
            throw new NoAccessException(NO_PERMISSION);
        }
        log.debug("User '{}' has permission: {}", user.getUsername(), requiredPermission);
    }

    private void validateUserFetchPermission(User user) {
        log.debug("Validating user fetch permission for user ID: {}", user.getId());
        User currentUser = getCurrentUser();
        assert currentUser != null;
        if (!isUserInTree(currentUser, user)) {
            log.warn("Current user '{}' does not have permission to fetch user '{}'",currentUser.getUsername(), user.getUsername());
            throw new DataNotFoundException(USER_NOT_FOUND);
        }
        log.debug("User fetch permission validated for user ID: {}", user.getId());
    }

    private void validateUserDeletionPermission(User userToDelete) {
        log.debug("Validating user deletion permission for user ID: {}", userToDelete.getId());
        User currentUser = getCurrentUser();
        if (userToDelete.isRootUser()) {
            if (!userToDelete.equals(currentUser)) { // Root user can be deleted by self only
                assert currentUser != null;
                log.warn("Attempted to delete root user '{}' by non-self user '{}'", userToDelete.getUsername(), currentUser.getUsername());
                throw new NoAccessException(NO_PERMISSION);
            }
        } else {
            assert currentUser != null;
            if (!isUserInTree(currentUser, userToDelete)) {
                log.warn("Current user '{}' does not have permission to delete user '{}'", currentUser.getUsername(), userToDelete.getUsername());
                throw new NoAccessException(NO_PERMISSION);
            }
        }
        log.debug("User deletion permission validated for user ID: {}", userToDelete.getId());
    }

    private boolean isUserInTree(User rootUser, User targetUser) {
        log.debug("Checking if user '{}' is in the hierarchy of user '{}'", targetUser.getUsername(), rootUser.getUsername());
        User currentUser = targetUser;
        while (currentUser != null) {
            if (currentUser.equals(rootUser)) {
                log.debug("User '{}' found in hierarchy of '{}'", targetUser.getUsername(), rootUser.getUsername());
                return true;
            }
            currentUser = currentUser.getCreatedBy();
        }
        log.debug("User '{}' not found in hierarchy of '{}'", targetUser.getUsername(), rootUser.getUsername());
        return false;
    }

    private User getCurrentUser() {
        log.debug("Attempting to retrieve current authenticated user");
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof DefaultUserDetails userDetails) {
            log.debug("Current user retrieved: {}", userDetails.getUsername());
            return userDetails.user();
        }
        log.warn("No authenticated user found in security context");
        return null;
    }

    private void validateRequest(UserRegistrationDto userDto) {
        log.debug("Validating user registration request for username: {}", userDto.getUsername());
        userValidator.validateUsernameAvailable(userDto.getUsername());
        userValidator.validateEmailAvailable(userDto.getEmail());
        log.debug("User registration request validated.");
    }

    private User getUserByUsername(String username) {
        log.debug("Attempting to retrieve user by username: {}", username);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found by username: {}", username);
                    return new DataNotFoundException(USER_NOT_FOUND);
                });
    }

    private void validatePasswordChangePermission(User currentUser, User userToUpdate) {
        log.debug("Validating password change permission for user: {}", userToUpdate.getUsername());
        if (currentUser.isRootUser() && isUserInTree(currentUser, userToUpdate)) {
            log.debug("Root user '{}' has permission to change password for subordinate user '{}'", currentUser.getUsername(), userToUpdate.getUsername());
            return; // Root user can change password of their subordinates
        }
        if (!currentUser.equals(userToUpdate)) {
            log.warn("User '{}' does not have permission to change password for user '{}'", currentUser.getUsername(), userToUpdate.getUsername());
            throw new NoAccessException(NO_PERMISSION);
        }
        log.debug("Password change permission validated for user: {}", userToUpdate.getUsername());
    }

    private void validateOldPassword(User user, String oldPassword) {
        log.debug("Validating old password for user: {}", user.getUsername());
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.warn("Invalid old password provided for user: {}", user.getUsername());
            throw new InvalidPasswordException(INVALID_OLD_PASSWORD);
        }
        log.debug("Old password validated for user: {}", user.getUsername());
    }

    private void updatePassword(User user, String newPassword) {
        log.debug("Updating password for user: {}", user.getUsername());
        userValidator.validatePasswordPolicy(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password updated successfully for user: {}", user.getUsername());
    }
}