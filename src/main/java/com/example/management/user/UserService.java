package com.example.management.user;

import com.example.management.dto.UserRegistrationDto;
import com.example.management.exception.DataNotFoundException;
import com.example.management.exception.UserAlreadyExistsException;
import com.example.management.role.Role;
import com.example.management.role.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private static final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    public User registerUser(UserRegistrationDto userDto){
        if(userExists(userDto.getUsername())){
            log.error("Username already exists: {}", userDto.getUsername());
            throw new UserAlreadyExistsException("Username not available");
        }
        if(userDto.getEmail() != null && emailExists(userDto.getEmail())) {
            log.error("Email already exists: {}", userDto.getEmail());
            throw new UserAlreadyExistsException("Email not available");
        }
        userDto.setPassword(passwordEncoder.encode(userDto.getPassword()));
        User user = userMapper.toEntity(userDto);
        log.info("Adding new User: {}", user.getUsername());

        attachRolesToUser(user, userDto.getRoleIds());
        return userRepository.save(user);
    }

    public void attachRoles(UserRoleAttachmentDto userRoleAttachmentDto) {
        if (userRoleAttachmentDto.getRoleIds().isEmpty()) {
            return;
        }

        User user = userRepository.findByUsername(userRoleAttachmentDto.getUsername())
                .orElseThrow(() -> new DataNotFoundException("User not found"));

        attachRolesToUser(user, userRoleAttachmentDto.getRoleIds());
    }

    private void attachRolesToUser(User user, Set<Long> roleIds) {
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new DataNotFoundException("Some roles not found");
        }

        user.setRoles(new HashSet<>(roles));
        userRepository.save(user);
    }

    private boolean userExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    private boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}
