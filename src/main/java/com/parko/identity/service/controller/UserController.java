package com.parko.identity.service.controller;

import com.parko.identity.service.dto.request.CreateUserRequest;
import com.parko.identity.service.dto.response.UserResponse;
import com.parko.identity.service.dto.response.UserWithBalanceAndVehiclesResponse;
import com.parko.identity.service.dto.response.UserWithBalanceResponse;
import com.parko.identity.service.dto.response.UserWithVehiclesResponse;
import com.parko.identity.service.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@RequestBody CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<UserWithBalanceResponse> getByIdWithBalance(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserWithBalance(id));
    }

    @GetMapping("/{id}/vehicles")
    public ResponseEntity<UserWithVehiclesResponse> getByIdWithVehicles(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserWithVehicles(id));
    }

    @GetMapping("/{id}/full")
    public ResponseEntity<UserWithBalanceAndVehiclesResponse> getByIdWithBalanceAndVehicles(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserWithBalanceAndVehicles(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
