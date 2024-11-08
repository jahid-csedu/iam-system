package com.example.iamsystem.user.model.dto;

import com.example.iamsystem.constant.ErrorMessage;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
