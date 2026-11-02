package com.microloan.Micro_loan.transaction.controller;

import com.microloan.Micro_loan.transaction.dto.CreateTransactionRequest;
import com.microloan.Micro_loan.transaction.dto.MoneyTransactionResponse;
import com.microloan.Micro_loan.transaction.service.MoneyTransactionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/transactions")
public class MoneyTransactionController {

    private final MoneyTransactionService transactionService;

    public MoneyTransactionController(MoneyTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public MoneyTransactionResponse requestMoney(Authentication authentication,
                                                 @Valid @RequestBody CreateTransactionRequest request) {
        return MoneyTransactionResponse.from(transactionService.requestMoney(currentEmail(authentication),
                request.otherUserEmail(), request.amount(), request.currency(), request.note(), request.incognito()));
    }

    @PostMapping("/offline")
    @ResponseStatus(HttpStatus.CREATED)
    public MoneyTransactionResponse recordOfflinePayment(Authentication authentication,
                                                         @Valid @RequestBody CreateTransactionRequest request) {
        return MoneyTransactionResponse.from(transactionService.recordOfflinePayment(currentEmail(authentication),
                request.otherUserEmail(), request.amount(), request.currency(), request.note(), request.incognito()));
    }

    @GetMapping("/requests/incoming")
    public List<MoneyTransactionResponse> incomingRequests(Authentication authentication) {
        return transactionService.incomingRequests(currentEmail(authentication)).stream()
                .map(MoneyTransactionResponse::from).toList();
    }

    @GetMapping("/requests/outgoing")
    public List<MoneyTransactionResponse> outgoingRequests(Authentication authentication) {
        return transactionService.outgoingRequests(currentEmail(authentication)).stream()
                .map(MoneyTransactionResponse::from).toList();
    }

    @PostMapping("/{transactionId}/accept")
    public MoneyTransactionResponse accept(Authentication authentication, @PathVariable UUID transactionId) {
        return MoneyTransactionResponse.from(transactionService.acceptRequest(currentEmail(authentication), transactionId));
    }

    @PostMapping("/{transactionId}/decline")
    public MoneyTransactionResponse decline(Authentication authentication, @PathVariable UUID transactionId) {
        return MoneyTransactionResponse.from(transactionService.declineRequest(currentEmail(authentication), transactionId));
    }

    @PostMapping("/{transactionId}/report-paid")
    public MoneyTransactionResponse reportPaid(Authentication authentication, @PathVariable UUID transactionId) {
        return MoneyTransactionResponse.from(transactionService.reportPaid(currentEmail(authentication), transactionId));
    }

    @PostMapping("/{transactionId}/confirm")
    public MoneyTransactionResponse confirm(Authentication authentication, @PathVariable UUID transactionId) {
        return MoneyTransactionResponse.from(transactionService.confirm(currentEmail(authentication), transactionId));
    }

    @PostMapping("/{transactionId}/dispute")
    public MoneyTransactionResponse dispute(Authentication authentication, @PathVariable UUID transactionId) {
        return MoneyTransactionResponse.from(transactionService.dispute(currentEmail(authentication), transactionId));
    }

    @GetMapping
    public List<MoneyTransactionResponse> history(Authentication authentication) {
        return transactionService.history(currentEmail(authentication)).stream().map(MoneyTransactionResponse::from).toList();
    }

    private static String currentEmail(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return authentication.getName();
    }
}
