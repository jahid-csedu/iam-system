package com.example.management.role;

import com.example.management.constant.ErrorMessage;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Collections;
import java.util.Set;

@Data
public class RoleDto {
    private Long id;
    @NotNull(message = ErrorMessage.ROLE_NAME_REQUIRED)
    private String name;
    private String description;
    private Set<Long> permissionIds = Collections.emptySet();
}
