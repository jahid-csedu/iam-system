package com.example.management.role;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class RolePermissionDto{
    @NotNull
    private Long roleId;
    @NotNull
    @NotEmpty
    private Set<Long> permissionIds;
}
