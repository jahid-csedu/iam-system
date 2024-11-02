package com.example.management.user.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class UserRoleAttachmentDto {
    @NotNull
    private String username;
    @NotNull
    @NotEmpty
    private Set<Long> roleIds;
}
