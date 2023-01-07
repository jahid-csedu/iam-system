package com.example.management.dto;

import com.example.management.constant.ErrorMessage;
import com.example.management.util.validators.email.ValidEmail;
import com.example.management.util.validators.password.PasswordMatches;
import lombok.*;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@PasswordMatches(message = ErrorMessage.PASSWORD_NOT_MATCHED)
public class UserRegistrationDto {

    @NotNull(message = ErrorMessage.USERNAME_REQUIRED)
    private String username;

    @NotNull(message = ErrorMessage.PASSWORD_REQUIRED)
    @Min(value = 6, message = ErrorMessage.PASSWORD_MIN_LENGHT)
    private String password;
    private String matchingPassword;
    private String fullName;

    @ValidEmail(message = ErrorMessage.INVALID_EMAIL)
    private String email;
}
