package com.parko.identity.service.service;

import com.parko.identity.service.dto.request.CreateVehicleRequest;
import com.parko.identity.service.dto.response.VehicleResponse;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.model.entity.VehicleEntity;
import com.parko.persistence.core.repository.UserRepository;
import com.parko.persistence.core.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private UserRepository userRepository;

    private VehicleService vehicleService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleService(vehicleRepository, userRepository);
        userId = UUID.randomUUID();
    }

    private CreateVehicleRequest validRequest() {
        return new CreateVehicleRequest("AB123CD", "Toyota", "Corolla");
    }

    @Test
    void createVehicle_happyPath_savesVehicle() {
        when(userRepository.existsById(userId)).thenReturn(true);
        when(vehicleRepository.findByUserId(userId)).thenReturn(List.of());
        when(vehicleRepository.save(any(VehicleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleResponse response = vehicleService.createVehicle(userId.toString(), validRequest());

        assertThat(response.id()).isNotNull();
        assertThat(response.plate()).isEqualTo("AB123CD");
        assertThat(response.brand()).isEqualTo("Toyota");
        assertThat(response.model()).isEqualTo("Corolla");
        assertThat(response.active()).isTrue();
    }

    @Test
    void createVehicle_userNotFound_throwsNoSuchElement() {
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> vehicleService.createVehicle(userId.toString(), validRequest()))
                .isInstanceOf(NoSuchElementException.class);

        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void createVehicle_invalidPlateFormat_throws() {
        when(userRepository.existsById(userId)).thenReturn(true);
        when(vehicleRepository.findByUserId(userId)).thenReturn(List.of());

        CreateVehicleRequest request = new CreateVehicleRequest("INVALID1", "Toyota", "Corolla");

        assertThatThrownBy(() -> vehicleService.createVehicle(userId.toString(), request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void createVehicle_brandTooLong_throws() {
        when(userRepository.existsById(userId)).thenReturn(true);
        when(vehicleRepository.findByUserId(userId)).thenReturn(List.of());

        CreateVehicleRequest request = new CreateVehicleRequest("AB123CD", "MarcaDemasiadoLarga", "Corolla");

        assertThatThrownBy(() -> vehicleService.createVehicle(userId.toString(), request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createVehicle_activeLimitReached_throws() {
        when(userRepository.existsById(userId)).thenReturn(true);
        UserEntity owner = new UserEntity();
        owner.setId(userId);
        List<VehicleEntity> threeActiveVehicles = List.of(
                activeVehicle(owner), activeVehicle(owner), activeVehicle(owner)
        );
        when(vehicleRepository.findByUserId(userId)).thenReturn(threeActiveVehicles);

        assertThatThrownBy(() -> vehicleService.createVehicle(userId.toString(), validRequest()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void createVehicle_activeLimitNotReachedWithInactiveOnes_succeeds() {
        when(userRepository.existsById(userId)).thenReturn(true);
        UserEntity owner = new UserEntity();
        owner.setId(userId);
        VehicleEntity inactive1 = activeVehicle(owner);
        inactive1.setActive(false);
        VehicleEntity inactive2 = activeVehicle(owner);
        inactive2.setActive(false);
        when(vehicleRepository.findByUserId(userId)).thenReturn(List.of(inactive1, inactive2));
        when(vehicleRepository.save(any(VehicleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleResponse response = vehicleService.createVehicle(userId.toString(), validRequest());

        assertThat(response.active()).isTrue();
    }

    @Test
    void getVehiclesByUser_userNotFound_throws() {
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> vehicleService.getVehiclesByUser(userId.toString()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getVehiclesByUser_returnsMappedVehicles() {
        UserEntity owner = new UserEntity();
        owner.setId(userId);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(vehicleRepository.findByUserId(userId)).thenReturn(List.of(activeVehicle(owner)));

        List<VehicleResponse> responses = vehicleService.getVehiclesByUser(userId.toString());

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).plate()).isEqualTo("AB123CD");
    }

    @Test
    void deleteVehicle_setsInactiveAndSaves() {
        UserEntity owner = new UserEntity();
        owner.setId(userId);
        VehicleEntity vehicle = activeVehicle(owner);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        vehicleService.deleteVehicle(vehicle.getId().toString());

        assertThat(vehicle.isActive()).isFalse();
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void deleteVehicle_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.deleteVehicle(id.toString()))
                .isInstanceOf(NoSuchElementException.class);
    }

    private VehicleEntity activeVehicle(UserEntity owner) {
        VehicleEntity entity = new VehicleEntity();
        entity.setId(UUID.randomUUID());
        entity.setUser(owner);
        entity.setPlate("AB123CD");
        entity.setBrand("Toyota");
        entity.setModel("Corolla");
        entity.setActive(true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
