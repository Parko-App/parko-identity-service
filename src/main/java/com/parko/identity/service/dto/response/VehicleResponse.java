package com.parko.identity.service.dto.response;

import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String plate,
        String brand,
        String model,
        boolean active
) {
}
