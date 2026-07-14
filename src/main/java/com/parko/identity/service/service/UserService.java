package com.parko.identity.service.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.parko.domain.lib.model.BalanceAccount;
import com.parko.domain.lib.model.Email;
import com.parko.domain.lib.model.User;
import com.parko.domain.lib.model.UserRole;
import com.parko.identity.service.converter.BalanceAccountConverter;
import com.parko.identity.service.converter.UserConverter;
import com.parko.identity.service.converter.VehicleConverter;
import com.parko.identity.service.dto.request.CreateUserRequest;
import com.parko.identity.service.dto.response.UserResponse;
import com.parko.identity.service.dto.response.UserWithBalanceAndVehiclesResponse;
import com.parko.identity.service.dto.response.UserWithBalanceResponse;
import com.parko.identity.service.dto.response.UserWithVehiclesResponse;
import com.parko.identity.service.dto.response.VehicleResponse;
import com.parko.persistence.core.model.embedded.BalanceAccountEmbedded;
import com.parko.persistence.core.model.embedded.UserEmbedded;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.model.entity.VehicleEntity;
import com.parko.persistence.core.repository.BalanceAccountRepository;
import com.parko.persistence.core.repository.UserRepository;
import com.parko.persistence.core.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BalanceAccountRepository balanceAccountRepository;
    private final VehicleRepository vehicleRepository;
    private final FirebaseAuth firebaseAuth;

    public UserService(UserRepository userRepository, BalanceAccountRepository balanceAccountRepository,
                        VehicleRepository vehicleRepository, FirebaseAuth firebaseAuth) {
        this.userRepository = userRepository;
        this.balanceAccountRepository = balanceAccountRepository;
        this.vehicleRepository = vehicleRepository;
        this.firebaseAuth = firebaseAuth;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (!request.termsAndConditionsAccepted()) {
            throw new IllegalArgumentException("Debe aceptar los términos y condiciones");
        }

        Email email = new Email(request.email());
        if (email.getInstitutionalDomain() != request.institutionalDomain()) {
            throw new IllegalArgumentException(
                    "El dominio institucional no coincide con el email: " + email.getInstitutionalDomain() + " vs " + request.institutionalDomain()
            );
        }

        User user = new User(
                UUID.randomUUID(),
                request.studentId(),
                request.fullName(),
                email
        );
        user.setFirebaseUid(createFirebaseUser(request));

        LocalDateTime now = LocalDateTime.now();
        UserEmbedded embedded = UserConverter.toEmbedded(user, UserRole.STUDENT, now, now);

        UserEntity entity = com.parko.persistence.core.converters.UserConverter.toEntity(embedded);
        UserEntity saved = userRepository.save(entity);

        BalanceAccount balanceAccount = new BalanceAccount(saved.getId());
        BalanceAccountEmbedded balanceAccountEmbedded = BalanceAccountConverter.toEmbedded(balanceAccount, now, now);
        BalanceAccountEntity balanceAccountEntity = com.parko.persistence.core.converters.BalanceAccountConverter.toEntity(balanceAccountEmbedded);
        balanceAccountRepository.save(balanceAccountEntity);

        UserEmbedded savedEmbedded = com.parko.persistence.core.converters.UserConverter.toEmbedded(saved);
        return UserConverter.toResponse(savedEmbedded);
    }

    public UserResponse getUser(String id) {
        UserEntity entity = findUserEntity(id);
        UserEmbedded embedded = com.parko.persistence.core.converters.UserConverter.toEmbedded(entity);
        return UserConverter.toResponse(embedded);
    }

    public UserWithBalanceResponse getUserWithBalance(String id) {
        UserEntity userEntity = findUserEntity(id);
        UserResponse user = UserConverter.toResponse(com.parko.persistence.core.converters.UserConverter.toEmbedded(userEntity));
        BigDecimal balance = findBalance(userEntity.getId());
        return new UserWithBalanceResponse(user, balance);
    }

    public UserWithVehiclesResponse getUserWithVehicles(String id) {
        UserEntity userEntity = findUserEntity(id);
        UserResponse user = UserConverter.toResponse(com.parko.persistence.core.converters.UserConverter.toEmbedded(userEntity));
        List<VehicleResponse> vehicles = findVehicles(userEntity.getId());
        return new UserWithVehiclesResponse(user, vehicles);
    }

    public UserWithBalanceAndVehiclesResponse getUserWithBalanceAndVehicles(String id) {
        UserEntity userEntity = findUserEntity(id);
        UserResponse user = UserConverter.toResponse(com.parko.persistence.core.converters.UserConverter.toEmbedded(userEntity));
        BigDecimal balance = findBalance(userEntity.getId());
        List<VehicleResponse> vehicles = findVehicles(userEntity.getId());
        return new UserWithBalanceAndVehiclesResponse(user, balance, vehicles);
    }

    private String createFirebaseUser(CreateUserRequest request) {
        try {
            UserRecord.CreateRequest firebaseRequest = new UserRecord.CreateRequest()
                    .setEmail(request.email())
                    .setPassword(request.password())
                    .setDisplayName(request.fullName());
            UserRecord userRecord = firebaseAuth.createUser(firebaseRequest);
            return userRecord.getUid();
        } catch (FirebaseAuthException e) {
            throw new IllegalArgumentException("No se pudo crear el usuario en Firebase: " + e.getMessage(), e);
        }
    }

    private UserEntity findUserEntity(String id) {
        UUID userId = UUID.fromString(id);
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + id));
    }

    private BigDecimal findBalance(UUID userId) {
        BalanceAccountEntity balanceAccountEntity = balanceAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Cuenta de saldo no encontrada para el usuario: " + userId));
        BalanceAccountEmbedded embedded = com.parko.persistence.core.converters.BalanceAccountConverter.toEmbedded(balanceAccountEntity);
        return embedded.balance();
    }

    private List<VehicleResponse> findVehicles(UUID userId) {
        List<VehicleEntity> vehicleEntities = vehicleRepository.findByUserId(userId);
        return vehicleEntities.stream()
                .map(entity -> VehicleConverter.toResponse(
                        entity.getId(),
                        com.parko.persistence.core.converters.VehicleConverter.toEmbedded(entity)))
                .toList();
    }

    @Transactional
    public void deleteUser(String id) {
        UserEntity entity = findUserEntity(id);
        entity.setActive(false);
        userRepository.save(entity);
    }
}
