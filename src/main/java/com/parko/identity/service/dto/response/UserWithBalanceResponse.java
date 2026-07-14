package com.parko.identity.service.dto.response;

import java.math.BigDecimal;

public record UserWithBalanceResponse(
        UserResponse user,
        BigDecimal balance
) {
}
