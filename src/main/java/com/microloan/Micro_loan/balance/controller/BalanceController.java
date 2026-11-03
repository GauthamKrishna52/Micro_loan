package com.microloan.Micro_loan.balance.controller;

import com.microloan.Micro_loan.balance.dto.BalanceResponse;
import com.microloan.Micro_loan.balance.service.BalanceSnapshotService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/balances")
public class BalanceController {

    private final BalanceSnapshotService balanceSnapshotService;

    public BalanceController(BalanceSnapshotService balanceSnapshotService) {
        this.balanceSnapshotService = balanceSnapshotService;
    }

    @GetMapping
    public BalanceResponse getBalance(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return balanceSnapshotService.getBalance(authentication.getName());
    }
}
