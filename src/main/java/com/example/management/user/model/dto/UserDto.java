package com.example.management.user.model.dto;

import lombok.Data;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private boolean rootUser;
}
