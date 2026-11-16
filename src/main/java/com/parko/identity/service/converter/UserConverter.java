package com.parko.identity.service.converter;

import com.parko.domain.lib.model.Email;
import com.parko.domain.lib.model.User;
import com.parko.domain.lib.model.UserRole;
import com.parko.identity.service.dto.response.UserResponse;
import com.parko.persistence.core.model.embedded.EmailEmbedded;
import com.parko.persistence.core.model.embedded.UserEmbedded;

import java.time.LocalDateTime;

public final class UserConverter {

    private UserConverter() {
    }

    public static UserEmbedded toEmbedded(User user, UserRole role, LocalDateTime createdAt, LocalDateTime updatedAt) {
        Email email = user.getEmail();
        EmailEmbedded emailEmbedded = new EmailEmbedded(email.getValue(), email.getStudentId(), email.getInstitutionalDomain());
        return new UserEmbedded(
                user.getId(),
                user.getStudentId(),
                user.getFullName(),
                emailEmbedded,
                user.getFirebaseUid(),
                role,
                user.isActive(),
                createdAt,
                updatedAt
        );
    }

    public static UserResponse toResponse(UserEmbedded embedded) {
        return new UserResponse(
                embedded.id(),
                embedded.studentId(),
                embedded.fullName(),
                embedded.email().value(),
                embedded.role(),
                embedded.active()
        );
    }

    public static User toDomain(UserEmbedded embedded) {
        Email email = new Email(embedded.email().value());
        User user = new User(
                embedded.id(),
                embedded.studentId(),
                embedded.fullName(),
                email
        );
        user.setFirebaseUid(embedded.firebaseUid());
        user.setActive(embedded.active());
        return user;
    }
}
