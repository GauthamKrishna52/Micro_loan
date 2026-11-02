package com.microloan.Micro_loan.connection.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateConnectionRequest(@NotBlank @Email String email) {
}
