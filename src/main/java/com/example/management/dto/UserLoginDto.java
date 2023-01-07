package com.example.management.dto;

import com.example.management.constant.ErrorMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginDto {
    @NotNull(message = ErrorMessage.USERNAME_REQUIRED)
    private String username;
    @NotNull(message = ErrorMessage.PASSWORD_REQUIRED)
    private String password;
}
