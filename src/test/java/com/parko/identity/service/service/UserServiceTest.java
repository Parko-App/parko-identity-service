package com.parko.identity.service.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.parko.domain.lib.model.InstitutionalDomain;
import com.parko.domain.lib.model.UserRole;
import com.parko.identity.service.dto.request.CreateUserRequest;
import com.parko.identity.service.dto.response.UserResponse;
import com.parko.identity.service.dto.response.UserWithBalanceAndVehiclesResponse;
import com.parko.identity.service.dto.response.UserWithBalanceResponse;
import com.parko.identity.service.dto.response.UserWithVehiclesResponse;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.model.entity.VehicleEntity;
import com.parko.persistence.core.repository.BalanceAccountRepository;
import com.parko.persistence.core.repository.UserRepository;
import com.parko.persistence.core.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BalanceAccountRepository balanceAccountRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private FirebaseAuth firebaseAuth;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, balanceAccountRepository, vehicleRepository, firebaseAuth);
    }

    private CreateUserRequest validRequest() {
        return new CreateUserRequest(
                "Juan Perez",
                "12345@frc.utn.edu.ar",
                "12345",
                "password123",
                InstitutionalDomain.FRC,
                true
        );
    }

    @Test
    void createUser_happyPath_savesUserAndBalanceAccount() throws FirebaseAuthException {
        UserRecord userRecord = mock(UserRecord.class);
        when(userRecord.getUid()).thenReturn("firebase-uid-123");
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(userRecord);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceAccountRepository.save(any(BalanceAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.createUser(validRequest());

        assertThat(response.studentId()).isEqualTo("12345");
        assertThat(response.fullName()).isEqualTo("Juan Perez");
        assertThat(response.email()).isEqualTo("12345@frc.utn.edu.ar");
        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
        assertThat(response.active()).isTrue();
        assertThat(response.id()).isNotNull();

        verify(balanceAccountRepository).save(any(BalanceAccountEntity.class));
    }

    @Test
    void createUser_termsNotAccepted_throws() {
        CreateUserRequest request = new CreateUserRequest(
                "Juan Perez", "12345@frc.utn.edu.ar", "12345", "password123", InstitutionalDomain.FRC, false);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_institutionalDomainMismatch_throws() {
        CreateUserRequest request = new CreateUserRequest(
                "Juan Perez", "12345@sistemas.frc.utn.edu.ar", "12345", "password123", InstitutionalDomain.FRC, true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_studentIdDoesNotMatchEmail_throws() {
        CreateUserRequest request = new CreateUserRequest(
                "Juan Perez", "12345@frc.utn.edu.ar", "99999", "password123", InstitutionalDomain.FRC, true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void getUser_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(id.toString()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getUser_found_returnsResponse() {
        UserEntity entity = sampleUserEntity();
        when(userRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        UserResponse response = userService.getUser(entity.getId().toString());

        assertThat(response.id()).isEqualTo(entity.getId());
        assertThat(response.studentId()).isEqualTo(entity.getStudentId());
    }

    @Test
    void getUserWithBalance_returnsUserAndBalance() {
        UserEntity userEntity = sampleUserEntity();
        BalanceAccountEntity balanceAccountEntity = new BalanceAccountEntity();
        balanceAccountEntity.setId(UUID.randomUUID());
        balanceAccountEntity.setUser(userEntity);
        balanceAccountEntity.setAmount(new BigDecimal("100.00"));
        balanceAccountEntity.setCreatedAt(LocalDateTime.now());
        balanceAccountEntity.setUpdatedAt(LocalDateTime.now());

        when(userRepository.findById(userEntity.getId())).thenReturn(Optional.of(userEntity));
        when(balanceAccountRepository.findByUserId(userEntity.getId())).thenReturn(Optional.of(balanceAccountEntity));

        UserWithBalanceResponse response = userService.getUserWithBalance(userEntity.getId().toString());

        assertThat(response.balance()).isEqualByComparingTo("100.00");
        assertThat(response.user().id()).isEqualTo(userEntity.getId());
    }

    @Test
    void getUserWithVehicles_returnsUserAndVehicles() {
        UserEntity userEntity = sampleUserEntity();
        VehicleEntity vehicleEntity = sampleVehicleEntity(userEntity);

        when(userRepository.findById(userEntity.getId())).thenReturn(Optional.of(userEntity));
        when(vehicleRepository.findByUserId(userEntity.getId())).thenReturn(List.of(vehicleEntity));

        UserWithVehiclesResponse response = userService.getUserWithVehicles(userEntity.getId().toString());

        assertThat(response.vehicles()).hasSize(1);
        assertThat(response.vehicles().get(0).plate()).isEqualTo("AB123CD");
    }

    @Test
    void getUserWithBalanceAndVehicles_returnsAllThree() {
        UserEntity userEntity = sampleUserEntity();
        VehicleEntity vehicleEntity = sampleVehicleEntity(userEntity);
        BalanceAccountEntity balanceAccountEntity = new BalanceAccountEntity();
        balanceAccountEntity.setId(UUID.randomUUID());
        balanceAccountEntity.setUser(userEntity);
        balanceAccountEntity.setAmount(BigDecimal.ZERO);
        balanceAccountEntity.setCreatedAt(LocalDateTime.now());
        balanceAccountEntity.setUpdatedAt(LocalDateTime.now());

        when(userRepository.findById(userEntity.getId())).thenReturn(Optional.of(userEntity));
        when(balanceAccountRepository.findByUserId(userEntity.getId())).thenReturn(Optional.of(balanceAccountEntity));
        when(vehicleRepository.findByUserId(userEntity.getId())).thenReturn(List.of(vehicleEntity));

        UserWithBalanceAndVehiclesResponse response =
                userService.getUserWithBalanceAndVehicles(userEntity.getId().toString());

        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.vehicles()).hasSize(1);
        assertThat(response.user().id()).isEqualTo(userEntity.getId());
    }

    @Test
    void deleteUser_setsInactiveAndSaves() {
        UserEntity entity = sampleUserEntity();
        when(userRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        userService.deleteUser(entity.getId().toString());

        assertThat(entity.isActive()).isFalse();
        verify(userRepository).save(entity);
    }

    @Test
    void deleteUser_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(id.toString()))
                .isInstanceOf(NoSuchElementException.class);
    }

    private UserEntity sampleUserEntity() {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setStudentId("12345");
        entity.setFullName("Juan Perez");
        entity.setEmail("12345@frc.utn.edu.ar");
        entity.setInstitutionalDomain(InstitutionalDomain.FRC);
        entity.setFirebaseUid("firebase-uid-1");
        entity.setRole(UserRole.STUDENT);
        entity.setActive(true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private VehicleEntity sampleVehicleEntity(UserEntity owner) {
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
