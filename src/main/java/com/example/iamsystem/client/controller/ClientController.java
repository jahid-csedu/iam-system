package com.example.iamsystem.client.controller;

import com.example.iamsystem.authorization.RequirePermission;
import com.example.iamsystem.client.dto.ClientDto;
import com.example.iamsystem.client.dto.ClientRequestDto;
import com.example.iamsystem.client.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.example.iamsystem.permission.PermissionKey.CLIENTS_CREATE;
import static com.example.iamsystem.permission.PermissionKey.CLIENTS_DELETE;
import static com.example.iamsystem.permission.PermissionKey.CLIENTS_READ;
import static com.example.iamsystem.permission.PermissionKey.CLIENTS_UPDATE;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    @RequirePermission(CLIENTS_READ)
    @Operation(summary = "List your tenant's clients, including the management console (Requires: clients:read)")
    public ResponseEntity<List<ClientDto>> getClients() {
        return ResponseEntity.ok(clientService.getClients());
    }

    @GetMapping("/{id}")
    @RequirePermission(CLIENTS_READ)
    @Operation(summary = "Get a client by ID (Requires: clients:read)")
    public ResponseEntity<ClientDto> getClient(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.getClient(id));
    }

    @PostMapping
    @RequirePermission(CLIENTS_CREATE)
    @Operation(summary = "Register an application client; its client_id is generated (Requires: clients:create)")
    public ResponseEntity<ClientDto> createClient(@RequestBody @Valid ClientRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.createClient(request));
    }

    @PutMapping("/{id}")
    @RequirePermission(CLIENTS_UPDATE)
    @Operation(summary = "Rename a client (Requires: clients:update)")
    public ResponseEntity<ClientDto> renameClient(@PathVariable Long id, @RequestBody @Valid ClientRequestDto request) {
        return ResponseEntity.ok(clientService.renameClient(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(CLIENTS_DELETE)
    @Operation(summary = "Delete an application client; the management console can't be deleted (Requires: clients:delete)")
    public ResponseEntity<Void> deleteClient(@PathVariable Long id) {
        clientService.deleteClient(id);
        return ResponseEntity.noContent().build();
    }
}
