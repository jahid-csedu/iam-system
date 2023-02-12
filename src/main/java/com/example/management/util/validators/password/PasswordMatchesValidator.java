package com.example.management.util.validators.password;

import com.example.management.dto.UserRegistrationDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, Object> {
    @Override
    public void initialize(PasswordMatches constraintAnnotation) {

    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        UserRegistrationDto user = (UserRegistrationDto) value;
        return user.getPassword().equals(user.getMatchingPassword());
    }
}