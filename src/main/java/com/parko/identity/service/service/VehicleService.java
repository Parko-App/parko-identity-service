package com.parko.identity.service.service;

import com.parko.domain.lib.model.Vehicle;
import com.parko.identity.service.converter.VehicleConverter;
import com.parko.identity.service.dto.request.CreateVehicleRequest;
import com.parko.identity.service.dto.response.VehicleResponse;
import com.parko.identity.service.exception.RegistrationLimitExceededException;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.model.embedded.VehicleEmbedded;
import com.parko.persistence.core.model.entity.VehicleEntity;
import com.parko.persistence.core.repository.UserRepository;
import com.parko.persistence.core.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class VehicleService {

    private static final int MAX_ACTIVE_VEHICLES = 3;
    /** Al borrar un vehiculo su patente queda como "PATENTE#id": libera la patente real sin perder la fila. */
    private static final String RELEASED_PLATE_SEPARATOR = "#";

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final int maxRegistrationsPerDay;

    public VehicleService(VehicleRepository vehicleRepository, UserRepository userRepository,
                          EntityManager entityManager,
                          @Value("${vehicles.max-registrations-per-day:5}") int maxRegistrationsPerDay) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.maxRegistrationsPerDay = maxRegistrationsPerDay;
    }

    public List<VehicleResponse> getVehiclesByUser(String userId, String firebaseUid) {
        UUID uid = parseUserId(userId);
        ensureOwnership(uid, firebaseUid);

        return vehicleRepository.findByUserIdAndActiveTrue(uid).stream()
                .map(entity -> VehicleConverter.toResponse(
                        entity.getId(),
                        com.parko.persistence.core.converters.VehicleConverter.toEmbedded(entity)))
                .toList();
    }

    @Transactional
    public VehicleResponse createVehicle(String userId, CreateVehicleRequest request, String firebaseUid) {
        UUID uid = parseUserId(userId);
        UserEntity owner = ensureOwnership(uid, firebaseUid);
        // Serializa las altas de vehiculos del mismo usuario: la fila del usuario queda bloqueada hasta
        // el commit, asi el conteo de activos de abajo no puede correr en paralelo con otro alta (TOCTOU).
        entityManager.lock(owner, LockModeType.PESSIMISTIC_WRITE);
        ensureActiveVehicleLimitNotReached(uid);
        ensureRegistrationRateNotExceeded(uid);

        Vehicle vehicle = new Vehicle(uid, request.plate(), request.brand(), request.model());

        LocalDateTime now = LocalDateTime.now();
        VehicleEmbedded embedded = VehicleConverter.toEmbedded(vehicle, now, now);

        VehicleEntity entity = com.parko.persistence.core.converters.VehicleConverter.toEntity(embedded);
        VehicleEntity saved = vehicleRepository.save(entity);

        VehicleEmbedded savedEmbedded = com.parko.persistence.core.converters.VehicleConverter.toEmbedded(saved);
        return VehicleConverter.toResponse(saved.getId(), savedEmbedded);
    }

    public VehicleResponse getVehicle(String id, String firebaseUid) {
        VehicleEntity entity = findOwnedVehicle(id, firebaseUid);
        VehicleResponse response = VehicleConverter.toResponse(
                entity.getId(),
                com.parko.persistence.core.converters.VehicleConverter.toEmbedded(entity));
        // un vehiculo borrado guarda "PATENTE#id": al cliente se le muestra la patente real
        return new VehicleResponse(response.id(), visiblePlate(response.plate()), response.brand(), response.model(),
                response.active());
    }

    @Transactional
    public void deleteVehicle(String id, String firebaseUid) {
        VehicleEntity entity = findOwnedVehicle(id, firebaseUid);
        if (!entity.isActive()) {
            return;
        }
        entity.setActive(false);
        // La columna plate es unica a nivel global: sin esto la patente quedaba ocupada para siempre
        // (tambien para su propio dueño) aunque el vehiculo estuviera borrado.
        entity.setPlate(entity.getPlate() + RELEASED_PLATE_SEPARATOR + entity.getId());
        vehicleRepository.save(entity);
    }

    private static String visiblePlate(String plate) {
        int separator = plate == null ? -1 : plate.indexOf(RELEASED_PLATE_SEPARATOR);
        return separator < 0 ? plate : plate.substring(0, separator);
    }

    private VehicleEntity findOwnedVehicle(String id, String firebaseUid) {
        UUID vehicleId = UUID.fromString(id);
        VehicleEntity entity = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NoSuchElementException("Vehículo no encontrado: " + id));
        if (!entity.getUser().getFirebaseUid().equals(firebaseUid)) {
            throw new AccessDeniedException("No tiene permisos sobre el vehículo: " + id);
        }
        return entity;
    }

    private UUID parseUserId(String userId) {
        return UUID.fromString(userId);
    }

    private UserEntity ensureOwnership(UUID userId, String firebaseUid) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + userId));
        if (!user.getFirebaseUid().equals(firebaseUid)) {
            throw new AccessDeniedException("No tiene permisos sobre el usuario: " + userId);
        }
        return user;
    }

    /**
     * Tope de altas por usuario en las ultimas 24 h, contando tambien los vehiculos ya borrados. Sin esto una
     * cuenta podia acumular patentes sin limite con el ciclo registrar -> borrar (el tope de 3 activos se libera
     * al borrar).
     */
    private void ensureRegistrationRateNotExceeded(UUID userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        long recentRegistrations = vehicleRepository.findByUserId(userId).stream()
                .filter(vehicle -> vehicle.getCreatedAt() != null && vehicle.getCreatedAt().isAfter(since))
                .count();
        if (recentRegistrations >= maxRegistrationsPerDay) {
            throw new RegistrationLimitExceededException(
                    "Alcanzaste el máximo de " + maxRegistrationsPerDay + " altas de vehículos por día");
        }
    }

    private void ensureActiveVehicleLimitNotReached(UUID userId) {
        long activeCount = vehicleRepository.findByUserIdAndActiveTrue(userId).size();
        if (activeCount >= MAX_ACTIVE_VEHICLES) {
            throw new IllegalArgumentException(
                    "El usuario ya tiene el máximo de " + MAX_ACTIVE_VEHICLES + " vehículos activos"
            );
        }
    }
}
