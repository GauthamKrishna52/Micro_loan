package com.microloan.Micro_loan.connection.dto;

import com.microloan.Micro_loan.connection.entity.ConnectionRequest;
import java.time.Instant;
import java.util.UUID;

public record ConnectionResponse(UUID userId, String name, String email, Instant connectedAt) {
    public static ConnectionResponse from(ConnectionRequest request, UUID currentUserId) {
        var otherUser = request.getRequester().getId().equals(currentUserId)
                ? request.getRecipient() : request.getRequester();
        return new ConnectionResponse(otherUser.getId(), otherUser.getName(), otherUser.getEmail(), request.getUpdatedAt());
    }
}
