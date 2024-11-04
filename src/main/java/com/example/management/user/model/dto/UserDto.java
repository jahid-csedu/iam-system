package com.example.management.user.model.dto;

import lombok.Data;

import java.util.Set;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private boolean rootUser;
    private Set<Long> roleIds;
}
