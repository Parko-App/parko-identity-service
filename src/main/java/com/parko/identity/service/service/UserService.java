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
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final int MIN_PASSWORD_LENGTH = 10;
    private static final Pattern SPECIAL_CHARACTER_PATTERN = Pattern.compile("[^A-Za-z0-9]");

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

        validatePassword(request.password());

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

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            throw new IllegalArgumentException("La contraseña debe contener al menos una letra mayúscula");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            throw new IllegalArgumentException("La contraseña debe contener al menos una letra minúscula");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            throw new IllegalArgumentException("La contraseña debe contener al menos un número");
        }
        if (!SPECIAL_CHARACTER_PATTERN.matcher(password).find()) {
            throw new IllegalArgumentException("La contraseña debe contener al menos un carácter especial");
        }
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

    private UserEntity findUserEntity(String firebaseUid) {
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + firebaseUid));
    }

    private BigDecimal findBalance(UUID userId) {
        BalanceAccountEntity balanceAccountEntity = balanceAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Cuenta de saldo no encontrada para el usuario: " + userId));
        BalanceAccountEmbedded embedded = com.parko.persistence.core.converters.BalanceAccountConverter.toEmbedded(balanceAccountEntity);
        return embedded.balance();
    }

    private List<VehicleResponse> findVehicles(UUID userId) {
        List<VehicleEntity> vehicleEntities = vehicleRepository.findByUserIdAndActiveTrue(userId);
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
