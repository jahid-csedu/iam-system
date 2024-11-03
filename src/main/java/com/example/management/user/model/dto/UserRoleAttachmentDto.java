package com.example.management.user.model.dto;

import com.example.management.constant.ErrorMessage;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class UserRoleAttachmentDto {
    @NotNull(message = ErrorMessage.USERNAME_REQUIRED)
    private String username;
    @NotNull(message = ErrorMessage.ROLE_ID_REQUIRED)
    @NotEmpty(message = ErrorMessage.ROLE_ID_NOT_EMPTY)
    private Set<Long> roleIds;
}
