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
                "Password123!",
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
                "Juan Perez", "12345@frc.utn.edu.ar", "12345", "Password123!", InstitutionalDomain.FRC, false);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_institutionalDomainMismatch_throws() {
        CreateUserRequest request = new CreateUserRequest(
                "Juan Perez", "12345@sistemas.frc.utn.edu.ar", "12345", "Password123!", InstitutionalDomain.FRC, true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_studentIdDoesNotMatchEmail_throws() {
        CreateUserRequest request = new CreateUserRequest(
                "Juan Perez", "12345@frc.utn.edu.ar", "99999", "Password123!", InstitutionalDomain.FRC, true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_passwordTooShort_throws() {
        CreateUserRequest request = requestWithPassword("Pass1!");

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 caracteres");

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_passwordWithoutUppercase_throws() {
        CreateUserRequest request = requestWithPassword("password123!");

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayúscula");

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_passwordWithoutLowercase_throws() {
        CreateUserRequest request = requestWithPassword("PASSWORD123!");

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minúscula");

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_passwordWithoutNumber_throws() {
        CreateUserRequest request = requestWithPassword("Password!!!");

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("número");

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_passwordWithoutSpecialCharacter_throws() {
        CreateUserRequest request = requestWithPassword("Password1234");

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("carácter especial");

        verify(userRepository, never()).save(any());
    }

    private CreateUserRequest requestWithPassword(String password) {
        return new CreateUserRequest(
                "Juan Perez", "12345@frc.utn.edu.ar", "12345", password, InstitutionalDomain.FRC, true);
    }

    @Test
    void getUser_notFound_throwsNoSuchElement() {
        when(userRepository.findByFirebaseUid("missing-uid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser("missing-uid"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getUser_found_returnsResponse() {
        UserEntity entity = sampleUserEntity();
        when(userRepository.findByFirebaseUid(entity.getFirebaseUid())).thenReturn(Optional.of(entity));

        UserResponse response = userService.getUser(entity.getFirebaseUid());

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

        when(userRepository.findByFirebaseUid(userEntity.getFirebaseUid())).thenReturn(Optional.of(userEntity));
        when(balanceAccountRepository.findByUserId(userEntity.getId())).thenReturn(Optional.of(balanceAccountEntity));

        UserWithBalanceResponse response = userService.getUserWithBalance(userEntity.getFirebaseUid());

        assertThat(response.balance()).isEqualByComparingTo("100.00");
        assertThat(response.user().id()).isEqualTo(userEntity.getId());
    }

    @Test
    void getUserWithVehicles_returnsUserAndVehicles() {
        UserEntity userEntity = sampleUserEntity();
        VehicleEntity vehicleEntity = sampleVehicleEntity(userEntity);

        when(userRepository.findByFirebaseUid(userEntity.getFirebaseUid())).thenReturn(Optional.of(userEntity));
        when(vehicleRepository.findByUserIdAndActiveTrue(userEntity.getId())).thenReturn(List.of(vehicleEntity));

        UserWithVehiclesResponse response = userService.getUserWithVehicles(userEntity.getFirebaseUid());

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

        when(userRepository.findByFirebaseUid(userEntity.getFirebaseUid())).thenReturn(Optional.of(userEntity));
        when(balanceAccountRepository.findByUserId(userEntity.getId())).thenReturn(Optional.of(balanceAccountEntity));
        when(vehicleRepository.findByUserIdAndActiveTrue(userEntity.getId())).thenReturn(List.of(vehicleEntity));

        UserWithBalanceAndVehiclesResponse response =
                userService.getUserWithBalanceAndVehicles(userEntity.getFirebaseUid());

        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.vehicles()).hasSize(1);
        assertThat(response.user().id()).isEqualTo(userEntity.getId());
    }

    @Test
    void deleteUser_setsInactiveAndSaves() {
        UserEntity entity = sampleUserEntity();
        when(userRepository.findByFirebaseUid(entity.getFirebaseUid())).thenReturn(Optional.of(entity));

        userService.deleteUser(entity.getFirebaseUid());

        assertThat(entity.isActive()).isFalse();
        verify(userRepository).save(entity);
    }

    @Test
    void deleteUser_notFound_throwsNoSuchElement() {
        when(userRepository.findByFirebaseUid("missing-uid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser("missing-uid"))
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
