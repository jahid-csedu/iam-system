package com.example.management.role;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionDto{
    @NotNull
    private Long roleId;
    @NotNull
    @NotEmpty
    private Set<Long> permissionIds;
}
