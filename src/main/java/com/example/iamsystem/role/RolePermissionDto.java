package com.example.iamsystem.role;

import com.example.iamsystem.constant.ErrorMessage;
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
    @NotNull(message = ErrorMessage.ROLE_LIST_REQUIRED)
    private Long roleId;
    @NotNull(message = ErrorMessage.PERMISSION_LIST_REQUIRED)
    @NotEmpty(message = ErrorMessage.PERMISSION_LIST_NOT_EMPTY)
    private Set<Long> permissionIds;
}
