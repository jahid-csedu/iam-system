package com.example.management.user;

import lombok.Data;

import java.util.Set;

@Data
public class UserRoleAttachmentDto {
    private String username;
    private Set<Long> roleIds;
}
