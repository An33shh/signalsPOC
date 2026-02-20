package com.signalspoc.api.controller;

import com.signalspoc.api.dto.response.UserResponse;
import com.signalspoc.domain.entity.User;
import com.signalspoc.domain.service.UserService;
import com.signalspoc.shared.model.Enums.ConnectorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User read operations")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Get all users with optional filters")
    public ResponseEntity<Page<UserResponse>> getUsers(
            @RequestParam(required = false) ConnectorType sourceSystem,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<User> users;
        if (sourceSystem != null) {
            users = userService.findBySourceSystem(sourceSystem, pageable);
        } else {
            users = userService.findAll(pageable);
        }
        Page<UserResponse> response = users.map(UserResponse::from);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by internal ID")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        User user = userService.findById(id);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @GetMapping("/{connector}/{externalId}")
    @Operation(summary = "Get user by connector and external ID")
    public ResponseEntity<UserResponse> getUserByExternalId(
            @PathVariable String connector,
            @PathVariable String externalId) {

        ConnectorType type = ConnectorType.valueOf(connector.toUpperCase());
        return userService.findByExternalId(externalId, type)
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElse(ResponseEntity.notFound().build());
    }
}
