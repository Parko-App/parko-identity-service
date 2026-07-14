package com.parko.identity.service.converter;

import com.parko.domain.lib.model.Vehicle;
import com.parko.identity.service.dto.response.VehicleResponse;
import com.parko.persistence.core.model.embedded.VehicleEmbedded;

import java.time.LocalDateTime;
import java.util.UUID;

public final class VehicleConverter {

    private VehicleConverter() {
    }

    public static VehicleEmbedded toEmbedded(Vehicle vehicle, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new VehicleEmbedded(
                vehicle.getUserId(),
                vehicle.getPlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.isActive(),
                createdAt,
                updatedAt
        );
    }

    public static VehicleResponse toResponse(UUID id, VehicleEmbedded embedded) {
        return new VehicleResponse(
                id,
                embedded.plate(),
                embedded.brand(),
                embedded.model(),
                embedded.active()
        );
    }
}
