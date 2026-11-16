package com.parko.identity.service.dto.request;

public record CreateVehicleRequest(
        String plate,
        String brand,
        String model
) {
}
