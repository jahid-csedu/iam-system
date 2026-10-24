package com.example.iamsystem.user.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.InvalidPasswordException;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.config.PasswordExpirationProperties;
import com.example.iamsystem.user.dto.PasswordChangeDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.iamsystem.common.exception.ErrorMessage.INVALID_OLD_PASSWORD;
import static com.example.iamsystem.common.util.DateUtil.calculateExpiryDate;

@Service
@RequiredArgsConstructor
public class UserPasswordService {

    private final UserRepository userRepository;
    private final UserValidator userValidator;
    private final UserAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;
    private final PasswordExpirationProperties passwordExpiration;

    /**
     * Changes the current user's password, or, when {@code username} is given, the password of that user in the same tenant.
     * Changing your own password always requires the old password.
     */
    @Auditable(
            value = AuditEventType.PASSWORD_CHANGE,
            target = "#username",
            detailsExpression = "T(java.util.Map).of('password_change_type', (#username != null ? 'admin_initiated' : 'user_initiated'))"
    )
    @Transactional
    public void changePassword(PasswordChangeDto request, String username) {
        User actor = currentUserProvider.requireCurrentUser();
        User target = username == null
                ? actor
                : userRepository.getRequiredByUsernameAndTenantId(username, actor.getTenant().getId());

        accessPolicy.checkCanChangePassword(actor, target);
        if (target.equals(actor)) {
            verifyOldPassword(actor, request.oldPassword());
        }
        userValidator.validatePasswordPolicy(request.newPassword());

        target.setPassword(passwordEncoder.encode(request.newPassword()));
        target.setPasswordExpiryDate(calculateExpiryDate(passwordExpiration.days()));
        userRepository.save(target);
    }

    private void verifyOldPassword(User user, String oldPassword) {
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new InvalidPasswordException(INVALID_OLD_PASSWORD);
        }
    }
}
