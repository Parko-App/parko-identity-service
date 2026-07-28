package com.parko.identity.service.controller;

import com.parko.identity.service.dto.request.CreateVehicleRequest;
import com.parko.identity.service.dto.response.VehicleResponse;
import com.parko.identity.service.service.VehicleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vehicle")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<VehicleResponse>> getByUser(@PathVariable String userId, Authentication authentication) {
        return ResponseEntity.ok(vehicleService.getVehiclesByUser(userId, authentication.getName()));
    }

    @PostMapping("/user/{userId}")
    public ResponseEntity<VehicleResponse> create(@PathVariable String userId, @RequestBody CreateVehicleRequest request,
                                                   Authentication authentication) {
        VehicleResponse created = vehicleService.createVehicle(userId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponse> getById(@PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok(vehicleService.getVehicle(id, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication authentication) {
        vehicleService.deleteVehicle(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
