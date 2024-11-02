package com.example.management.user.util;

import com.example.management.exception.DataNotFoundException;
import com.example.management.role.Role;
import com.example.management.role.RoleRepository;
import com.example.management.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRoleAttachmentUtil {
    private final RoleRepository roleRepository;

    public Set<Role> validateAndRetrieveRoles(Set<Long> roleIds) {
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new DataNotFoundException("Some roles not found");
        }
        return new HashSet<>(roles);
    }

    public void assignRolesToUser(User user, Set<Role> roles) {
        user.setRoles(roles);
    }
}
