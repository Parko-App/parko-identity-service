package com.parko.identity.service.dto.response;

import java.util.List;

public record UserWithVehiclesResponse(
        UserResponse user,
        List<VehicleResponse> vehicles
) {
}
