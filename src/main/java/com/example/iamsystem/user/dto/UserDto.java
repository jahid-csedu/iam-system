package com.example.iamsystem.user.dto;

import com.example.iamsystem.user.entity.UserType;
import lombok.Data;

import java.util.Set;

@Data
public class UserDto {
    private Long id;
    private Long tenantId;
    private String username;
    private String fullName;
    private String email;
    private boolean rootUser;
    private UserType userType;
    private String createdBy;
    private Set<Long> roleIds;
    private Set<Long> permissionIds;
}
