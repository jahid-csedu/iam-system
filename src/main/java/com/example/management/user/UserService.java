package com.example.management.user;

import com.example.management.dto.UserRegistrationDto;
import com.example.management.exception.UserAlreadyExistsException;
import com.example.management.role.Role;
import com.example.management.role.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class UserService {

    private static final String USER_ROLE_NAME = "ROLE_USER";


    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(UserRegistrationDto userDto) throws UserAlreadyExistsException{
        if(userExists(userDto.getUsername())){
            log.error("Username already exists: {}", userDto.getUsername());
            throw new UserAlreadyExistsException("Username not available");
        }
        if(userDto.getEmail() != null && emailExists(userDto.getEmail())) {
            log.error("Email already exists: {}", userDto.getEmail());
            throw new UserAlreadyExistsException("Email not available");
        }
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(userDto.getUsername());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setEmail(userDto.getEmail());
        user.setFullName(userDto.getFullName());
        Optional<Role> role = roleRepository.findByName(USER_ROLE_NAME);
        if(role.isPresent()) {
            user.getRoles().add(role.get());
        }else {
            Role role1 = new Role();
            role1.setId(UUID.randomUUID().toString());
            role1.setName(USER_ROLE_NAME);
            roleRepository.save(role1);
            user.getRoles().add(role1);
        }
        user.setUserLocked(false);
        user.setActive(true);
        user.setPasswordExpired(false);
        log.info("Adding new User: {}", user.getUsername());
        return userRepository.save(user);
    }

    private boolean userExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    private boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}
