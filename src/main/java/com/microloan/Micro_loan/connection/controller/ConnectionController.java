package com.microloan.Micro_loan.connection.controller;

import com.microloan.Micro_loan.connection.dto.ConnectionRequestResponse;
import com.microloan.Micro_loan.connection.dto.ConnectionResponse;
import com.microloan.Micro_loan.connection.dto.CreateConnectionRequest;
import com.microloan.Micro_loan.connection.service.ConnectionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping("/requests")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public ConnectionRequestResponse sendRequest(Authentication authentication,
                                                 @Valid @RequestBody CreateConnectionRequest request) {
        return ConnectionRequestResponse.from(connectionService.sendRequest(currentEmail(authentication), request.email()));
    }

    @GetMapping("/requests/incoming")
    public List<ConnectionRequestResponse> incoming(Authentication authentication) {
        return connectionService.incoming(currentEmail(authentication)).stream().map(ConnectionRequestResponse::from).toList();
    }

    @GetMapping("/requests/outgoing")
    public List<ConnectionRequestResponse> outgoing(Authentication authentication) {
        return connectionService.outgoing(currentEmail(authentication)).stream().map(ConnectionRequestResponse::from).toList();
    }

    @PostMapping("/requests/{requestId}/accept")
    public ConnectionRequestResponse accept(Authentication authentication, @PathVariable UUID requestId) {
        return ConnectionRequestResponse.from(connectionService.respond(currentEmail(authentication), requestId, true));
    }

    @PostMapping("/requests/{requestId}/decline")
    public ConnectionRequestResponse decline(Authentication authentication, @PathVariable UUID requestId) {
        return ConnectionRequestResponse.from(connectionService.respond(currentEmail(authentication), requestId, false));
    }

    @GetMapping
    public List<ConnectionResponse> connections(Authentication authentication) {
        String email = currentEmail(authentication);
        // The authenticated principal is resolved by the service; response mapping uses the request participants.
        return connectionService.connections(email).stream()
                .map(request -> ConnectionResponse.from(request, request.getRequester().getEmail().equals(email)
                        ? request.getRequester().getId() : request.getRecipient().getId()))
                .toList();
    }

    @DeleteMapping("/{userId}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeConnection(Authentication authentication, @PathVariable UUID userId) {
        connectionService.removeConnection(currentEmail(authentication), userId);
    }

    private static String currentEmail(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return authentication.getName();
    }
}
