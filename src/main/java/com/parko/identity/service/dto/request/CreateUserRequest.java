package com.parko.identity.service.dto.request;

import com.parko.domain.lib.model.InstitutionalDomain;

public record CreateUserRequest(
        String fullName,
        String email,
        String studentId,
        String password,
        InstitutionalDomain institutionalDomain,
        boolean termsAndConditionsAccepted
) {
}
