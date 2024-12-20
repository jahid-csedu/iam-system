package com.example.iamsystem.user.model.dto;

import lombok.Data;

import java.util.Set;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private boolean rootUser;
    private String createdBy;
    private Set<Long> roleIds;
}
