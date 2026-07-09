package com.parko.identity.service.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record UserWithBalanceAndVehiclesResponse(
        UserResponse user,
        BigDecimal balance,
        List<VehicleResponse> vehicles
) {
}
