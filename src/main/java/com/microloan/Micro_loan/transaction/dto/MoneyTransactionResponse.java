package com.microloan.Micro_loan.transaction.dto;

import com.microloan.Micro_loan.transaction.entity.MoneyTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MoneyTransactionResponse(UUID id, UUID payerId, String payerName, String payerEmail,
                                       UUID recipientId, String recipientName, String recipientEmail,
                                       BigDecimal amount, String currency, String note,
                                       MoneyTransaction.Visibility visibility, MoneyTransaction.Origin origin,
                                       MoneyTransaction.Status status, Instant createdAt, Instant updatedAt) {
    public static MoneyTransactionResponse from(MoneyTransaction transaction) {
        return new MoneyTransactionResponse(transaction.getId(),
                transaction.getPayer().getId(), transaction.getPayer().getName(), transaction.getPayer().getEmail(),
                transaction.getRecipient().getId(), transaction.getRecipient().getName(), transaction.getRecipient().getEmail(),
                transaction.getAmount(), transaction.getCurrency(), transaction.getNote(), transaction.getVisibility(),
                transaction.getOrigin(), transaction.getStatus(), transaction.getCreatedAt(), transaction.getUpdatedAt());
    }
}
