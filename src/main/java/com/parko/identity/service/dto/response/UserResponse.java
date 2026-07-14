package com.parko.identity.service.dto.response;

import com.parko.domain.lib.model.UserRole;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String studentId,
        String fullName,
        String email,
        UserRole role,
        boolean active
) {
}
