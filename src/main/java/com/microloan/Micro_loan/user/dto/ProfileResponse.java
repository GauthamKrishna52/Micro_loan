package com.microloan.Micro_loan.user.dto;

import java.time.Instant;
import java.util.UUID;

public record ProfileResponse(UUID id, String name, Instant createdAt) {
}
