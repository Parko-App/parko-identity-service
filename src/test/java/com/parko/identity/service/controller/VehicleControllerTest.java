package com.parko.identity.service.controller;

import tools.jackson.databind.ObjectMapper;
import com.parko.identity.service.dto.request.CreateVehicleRequest;
import com.parko.identity.service.dto.response.VehicleResponse;
import com.parko.identity.service.exception.GlobalExceptionHandler;
import com.parko.identity.service.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VehicleController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class VehicleControllerTest {

    private static final String FIREBASE_UID = "firebase-uid-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VehicleService vehicleService;

    private <T extends MockHttpServletRequestBuilder> T withAuth(T builder) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(FIREBASE_UID, null, List.of());
        builder.principal(authentication);
        return builder;
    }

    @Test
    void getByUser_returnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        VehicleResponse vehicle = new VehicleResponse(UUID.randomUUID(), "AB123CD", "Toyota", "Corolla", true);
        when(vehicleService.getVehiclesByUser(userId.toString(), FIREBASE_UID)).thenReturn(List.of(vehicle));

        mockMvc.perform(withAuth(get("/api/vehicle/user/{userId}", userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plate").value("AB123CD"));
    }

    @Test
    void getByUser_userNotFound_returnsNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        when(vehicleService.getVehiclesByUser(userId.toString(), FIREBASE_UID))
                .thenThrow(new NoSuchElementException("Usuario no encontrado: " + userId));

        mockMvc.perform(withAuth(get("/api/vehicle/user/{userId}", userId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByUser_notOwner_returnsForbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        when(vehicleService.getVehiclesByUser(userId.toString(), FIREBASE_UID))
                .thenThrow(new AccessDeniedException("No tiene permisos sobre el usuario: " + userId));

        mockMvc.perform(withAuth(get("/api/vehicle/user/{userId}", userId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_returnsCreated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        CreateVehicleRequest request = new CreateVehicleRequest("AB123CD", "Toyota", "Corolla");
        VehicleResponse response = new VehicleResponse(vehicleId, "AB123CD", "Toyota", "Corolla", true);
        when(vehicleService.createVehicle(eq(userId.toString()), any(CreateVehicleRequest.class), eq(FIREBASE_UID)))
                .thenReturn(response);

        mockMvc.perform(withAuth(post("/api/vehicle/user/{userId}", userId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(vehicleId.toString()))
                .andExpect(jsonPath("$.plate").value("AB123CD"));
    }

    @Test
    void create_activeLimitReached_returnsBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateVehicleRequest request = new CreateVehicleRequest("AB123CD", "Toyota", "Corolla");
        when(vehicleService.createVehicle(eq(userId.toString()), any(CreateVehicleRequest.class), eq(FIREBASE_UID)))
                .thenThrow(new IllegalArgumentException("El usuario ya tiene el máximo de 3 vehículos activos"));

        mockMvc.perform(withAuth(post("/api/vehicle/user/{userId}", userId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        VehicleResponse vehicle = new VehicleResponse(id, "AB123CD", "Toyota", "Corolla", true);
        when(vehicleService.getVehicle(id.toString(), FIREBASE_UID)).thenReturn(vehicle);

        mockMvc.perform(withAuth(get("/api/vehicle/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plate").value("AB123CD"));
    }

    @Test
    void getById_notFound_returnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(vehicleService.getVehicle(id.toString(), FIREBASE_UID))
                .thenThrow(new NoSuchElementException("Vehículo no encontrado: " + id));

        mockMvc.perform(withAuth(get("/api/vehicle/{id}", id)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_notOwner_returnsForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        when(vehicleService.getVehicle(id.toString(), FIREBASE_UID))
                .thenThrow(new AccessDeniedException("No tiene permisos sobre el vehículo: " + id));

        mockMvc.perform(withAuth(get("/api/vehicle/{id}", id)))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_duplicatePlate_returnsConflict() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateVehicleRequest request = new CreateVehicleRequest("AB123CD", "Toyota", "Corolla");
        when(vehicleService.createVehicle(eq(userId.toString()), any(CreateVehicleRequest.class), eq(FIREBASE_UID)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"vehicles_plate_key\""));

        mockMvc.perform(withAuth(post("/api/vehicle/user/{userId}", userId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El recurso ya existe o entra en conflicto con datos existentes"));
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(withAuth(delete("/api/vehicle/{id}", id)))
                .andExpect(status().isNoContent());

        verify(vehicleService).deleteVehicle(eq(id.toString()), eq(FIREBASE_UID));
    }

    @Test
    void delete_notFound_returnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new NoSuchElementException("Vehículo no encontrado: " + id))
                .when(vehicleService).deleteVehicle(id.toString(), FIREBASE_UID);

        mockMvc.perform(withAuth(delete("/api/vehicle/{id}", id)))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_notOwner_returnsForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new AccessDeniedException("No tiene permisos sobre el vehículo: " + id))
                .when(vehicleService).deleteVehicle(id.toString(), FIREBASE_UID);

        mockMvc.perform(withAuth(delete("/api/vehicle/{id}", id)))
                .andExpect(status().isForbidden());
    }
}
