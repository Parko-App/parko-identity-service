package com.parko.identity.service.controller;

import tools.jackson.databind.ObjectMapper;
import com.parko.domain.lib.model.InstitutionalDomain;
import com.parko.domain.lib.model.UserRole;
import com.parko.identity.service.dto.request.CreateUserRequest;
import com.parko.identity.service.dto.response.UserResponse;
import com.parko.identity.service.dto.response.UserWithBalanceAndVehiclesResponse;
import com.parko.identity.service.dto.response.UserWithBalanceResponse;
import com.parko.identity.service.dto.response.UserWithVehiclesResponse;
import com.parko.identity.service.dto.response.VehicleResponse;
import com.parko.identity.service.exception.GlobalExceptionHandler;
import com.parko.identity.service.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private UserResponse sampleUserResponse(UUID id) {
        return new UserResponse(id, "12345", "Juan Perez", "12345@frc.utn.edu.ar", UserRole.STUDENT, true);
    }

    @Test
    void create_returnsCreated() throws Exception {
        UUID id = UUID.randomUUID();
        CreateUserRequest request = new CreateUserRequest(
                "Juan Perez", "12345@frc.utn.edu.ar", "12345", "password123", InstitutionalDomain.FRC, true);
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(sampleUserResponse(id));

        mockMvc.perform(post("/api/user")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.studentId").value("12345"));
    }

    @Test
    void create_serviceThrowsIllegalArgument_returnsBadRequest() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "Juan Perez", "12345@frc.utn.edu.ar", "12345", "password123", InstitutionalDomain.FRC, false);
        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(new IllegalArgumentException("Debe aceptar los términos y condiciones"));

        mockMvc.perform(post("/api/user")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe aceptar los términos y condiciones"));
    }

    @Test
    void getById_found_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUser(id.toString())).thenReturn(sampleUserResponse(id));

        mockMvc.perform(get("/api/user/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getById_notFound_returnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUser(id.toString())).thenThrow(new NoSuchElementException("Usuario no encontrado: " + id));

        mockMvc.perform(get("/api/user/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByIdWithBalance_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        UserWithBalanceResponse response = new UserWithBalanceResponse(sampleUserResponse(id), new BigDecimal("50.00"));
        when(userService.getUserWithBalance(id.toString())).thenReturn(response);

        mockMvc.perform(get("/api/user/{id}/balance", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50.00));
    }

    @Test
    void getByIdWithVehicles_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        VehicleResponse vehicle = new VehicleResponse(UUID.randomUUID(), "AB123CD", "Toyota", "Corolla", true);
        UserWithVehiclesResponse response = new UserWithVehiclesResponse(sampleUserResponse(id), List.of(vehicle));
        when(userService.getUserWithVehicles(id.toString())).thenReturn(response);

        mockMvc.perform(get("/api/user/{id}/vehicles", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicles[0].plate").value("AB123CD"));
    }

    @Test
    void getByIdWithBalanceAndVehicles_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        VehicleResponse vehicle = new VehicleResponse(UUID.randomUUID(), "AB123CD", "Toyota", "Corolla", true);
        UserWithBalanceAndVehiclesResponse response =
                new UserWithBalanceAndVehiclesResponse(sampleUserResponse(id), BigDecimal.TEN, List.of(vehicle));
        when(userService.getUserWithBalanceAndVehicles(id.toString())).thenReturn(response);

        mockMvc.perform(get("/api/user/{id}/full", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10))
                .andExpect(jsonPath("$.vehicles[0].brand").value("Toyota"));
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/user/{id}", id))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(eq(id.toString()));
    }

    @Test
    void delete_notFound_returnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new NoSuchElementException("Usuario no encontrado: " + id))
                .when(userService).deleteUser(id.toString());

        mockMvc.perform(delete("/api/user/{id}", id))
                .andExpect(status().isNotFound());
    }
}
