package com.example.iamsystem.role.model;

import com.example.iamsystem.constant.ErrorMessage;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleDto {
    private Long id;
    @NotNull(message = ErrorMessage.ROLE_NAME_REQUIRED)
    private String name;
    private String description;
    private Set<Long> permissionIds = Collections.emptySet();
}
