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
    @NotNull(message = ErrorMessage.ROLE_ID_REQUIRED)
    private Long roleId;
    @NotNull(message = ErrorMessage.PERMISSION_ID_REQUIRED)
    @NotEmpty(message = ErrorMessage.PERMISSION_ID_NOT_EMPTY)
    private Set<Long> permissionIds;
}
