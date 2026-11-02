package com.microloan.Micro_loan.transaction.controller;

import com.microloan.Micro_loan.transaction.dto.BalanceResponse;
import com.microloan.Micro_loan.transaction.service.NetworkBalanceService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/balances")
public class BalanceController {

    private final NetworkBalanceService balanceService;

    public BalanceController(NetworkBalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping
    public BalanceResponse getNetworkBalances(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return balanceService.calculate(authentication.getName());
    }
}
