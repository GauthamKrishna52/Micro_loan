package com.microloan.Micro_loan.connection.dto;

import com.microloan.Micro_loan.connection.entity.ConnectionRequest;
import java.time.Instant;
import java.util.UUID;

public record ConnectionRequestResponse(UUID id, UUID requesterId, String requesterName, String requesterEmail,
                                       UUID recipientId, String recipientName, String recipientEmail,
                                       ConnectionRequest.Status status, Instant createdAt, Instant updatedAt) {
    public static ConnectionRequestResponse from(ConnectionRequest request) {
        return new ConnectionRequestResponse(request.getId(),
                request.getRequester().getId(), request.getRequester().getName(), request.getRequester().getEmail(),
                request.getRecipient().getId(), request.getRecipient().getName(), request.getRecipient().getEmail(),
                request.getStatus(), request.getCreatedAt(), request.getUpdatedAt());
    }
}
