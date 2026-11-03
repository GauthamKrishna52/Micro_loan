package com.microloan.Micro_loan.balance.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BalanceResponse(UUID currentUserId, String calculatedAt, List<CurrencyBalance> currencies) {
    public record CurrencyBalance(String currency, BigDecimal netAmount, BigDecimal totalPaid,
                                  BigDecimal totalReceived, List<IncludedTransaction> includedTransactions,
                                  List<SuggestedPayment> suggestedPayments) { }

    public record IncludedTransaction(UUID transactionId, Direction direction, UUID counterpartyId,
                                      String counterpartyName, BigDecimal amount, String recordedAt) { }

    public record SuggestedPayment(UUID fromUserId, String fromName, UUID toUserId, String toName,
                                   BigDecimal amount) { }

    public enum Direction { PAID, RECEIVED }
}
