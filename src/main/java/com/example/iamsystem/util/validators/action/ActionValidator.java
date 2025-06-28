package com.example.iamsystem.util.validators.action;

import com.example.iamsystem.permission.model.PermissionAction;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ActionValidator implements ConstraintValidator<ValidAction, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        if (value == null) {
            return true;
        }
        try {
            PermissionAction.valueOf(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
