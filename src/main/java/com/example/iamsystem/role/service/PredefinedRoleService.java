package com.example.iamsystem.role.service;

import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.entity.RoleTemplate;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.role.repository.RoleTemplateRepository;
import com.example.iamsystem.tenant.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gives a new tenant its own copies of the predefined roles. The copies are ordinary roles: the tenant may change or
 * delete them, and later changes to the templates never reach them.
 */
@Service
@RequiredArgsConstructor
public class PredefinedRoleService {

    private final RoleTemplateRepository roleTemplateRepository;
    private final RoleRepository roleRepository;

    /**
     * Runs inside the signup transaction, so a failed signup leaves no roles behind.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Role> createPredefinedRoles(Tenant tenant) {
        List<Role> roles = roleTemplateRepository.findAll().stream()
                .map(template -> copyOf(template, tenant))
                .toList();
        return roleRepository.saveAll(roles);
    }

    private static Role copyOf(RoleTemplate template, Tenant tenant) {
        Role role = new Role();
        role.setName(template.getName());
        role.setDescription(template.getDescription());
        role.setTenant(tenant);
        role.getPermissions().addAll(template.getPermissions());
        return role;
    }
}
