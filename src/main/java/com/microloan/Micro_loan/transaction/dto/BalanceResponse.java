package com.microloan.Micro_loan.transaction.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BalanceResponse(UUID currentUserId, List<CurrencyBalance> currencies) {
    public record CurrencyBalance(String currency, List<MemberBalance> members, List<SuggestedPayment> suggestedPayments) { }
    public record MemberBalance(UUID userId, String name, BigDecimal netAmount) { }
    public record SuggestedPayment(UUID fromUserId, String fromName, UUID toUserId, String toName, BigDecimal amount) { }
}
