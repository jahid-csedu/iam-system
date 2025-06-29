package com.example.iamsystem.security.user;

import com.example.iamsystem.user.model.entity.User;
import com.example.iamsystem.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultUserDetailsService implements UserDetailsService {

    private final UserRepository repository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Attempting to load user by username: {}", username);
        Optional<User> user = repository.findByUsername(username);
        if (user.isEmpty()) {
            log.warn("User not found with username: {}", username);
            throw new UsernameNotFoundException("User doesn't exist");
        }
        log.info("User '{}' loaded successfully.", username);
        return new DefaultUserDetails(user.get());
    }
}
