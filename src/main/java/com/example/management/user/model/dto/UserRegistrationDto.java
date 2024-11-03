package com.example.management.user.model.dto;

import com.example.management.constant.ErrorMessage;
import com.example.management.util.validators.email.ValidEmail;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserRegistrationDto {

    @NotNull(message = ErrorMessage.USERNAME_REQUIRED)
    private String username;

    @NotNull(message = ErrorMessage.PASSWORD_REQUIRED)
    @Length(min = 6, max = 15, message = ErrorMessage.PASSWORD_LENGTH)
    private String password;
    private String fullName;

    @ValidEmail(message = ErrorMessage.INVALID_EMAIL)
    private String email;
    private boolean rootUser;

    private Set<Long> roleIds = new HashSet<>();
}
