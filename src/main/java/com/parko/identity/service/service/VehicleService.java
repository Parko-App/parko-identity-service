package com.parko.identity.service.service;

import com.parko.domain.lib.model.Vehicle;
import com.parko.identity.service.converter.VehicleConverter;
import com.parko.identity.service.dto.request.CreateVehicleRequest;
import com.parko.identity.service.dto.response.VehicleResponse;
import com.parko.persistence.core.model.embedded.VehicleEmbedded;
import com.parko.persistence.core.model.entity.VehicleEntity;
import com.parko.persistence.core.repository.UserRepository;
import com.parko.persistence.core.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class VehicleService {

    private static final int MAX_ACTIVE_VEHICLES = 3;

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    public VehicleService(VehicleRepository vehicleRepository, UserRepository userRepository) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
    }

    public List<VehicleResponse> getVehiclesByUser(String userId) {
        UUID uid = parseUserId(userId);
        ensureUserExists(uid);

        return vehicleRepository.findByUserId(uid).stream()
                .map(entity -> VehicleConverter.toResponse(
                        entity.getId(),
                        com.parko.persistence.core.converters.VehicleConverter.toEmbedded(entity)))
                .toList();
    }

    @Transactional
    public VehicleResponse createVehicle(String userId, CreateVehicleRequest request) {
        UUID uid = parseUserId(userId);
        ensureUserExists(uid);
        ensureActiveVehicleLimitNotReached(uid);

        Vehicle vehicle = new Vehicle(uid, request.plate(), request.brand(), request.model());

        LocalDateTime now = LocalDateTime.now();
        VehicleEmbedded embedded = VehicleConverter.toEmbedded(vehicle, now, now);

        VehicleEntity entity = com.parko.persistence.core.converters.VehicleConverter.toEntity(embedded);
        VehicleEntity saved = vehicleRepository.save(entity);

        VehicleEmbedded savedEmbedded = com.parko.persistence.core.converters.VehicleConverter.toEmbedded(saved);
        return VehicleConverter.toResponse(saved.getId(), savedEmbedded);
    }

    @Transactional
    public void deleteVehicle(String id) {
        UUID vehicleId = UUID.fromString(id);
        VehicleEntity entity = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NoSuchElementException("Vehículo no encontrado: " + id));
        entity.setActive(false);
        vehicleRepository.save(entity);
    }

    private UUID parseUserId(String userId) {
        return UUID.fromString(userId);
    }

    private void ensureUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new NoSuchElementException("Usuario no encontrado: " + userId);
        }
    }

    private void ensureActiveVehicleLimitNotReached(UUID userId) {
        long activeCount = vehicleRepository.findByUserId(userId).stream()
                .filter(VehicleEntity::isActive)
                .count();
        if (activeCount >= MAX_ACTIVE_VEHICLES) {
            throw new IllegalArgumentException(
                    "El usuario ya tiene el máximo de " + MAX_ACTIVE_VEHICLES + " vehículos activos"
            );
        }
    }
}
