package com.microloan.Micro_loan.transaction.service;

import com.microloan.Micro_loan.connection.repository.ConnectionRequestRepository;
import com.microloan.Micro_loan.transaction.dto.BalanceResponse;
import com.microloan.Micro_loan.transaction.dto.BalanceResponse.CurrencyBalance;
import com.microloan.Micro_loan.transaction.dto.BalanceResponse.MemberBalance;
import com.microloan.Micro_loan.transaction.dto.BalanceResponse.SuggestedPayment;
import com.microloan.Micro_loan.transaction.entity.MoneyTransaction;
import com.microloan.Micro_loan.transaction.entity.MoneyTransaction.Status;
import com.microloan.Micro_loan.transaction.entity.MoneyTransaction.Visibility;
import com.microloan.Micro_loan.transaction.repository.MoneyTransactionRepository;
import com.microloan.Micro_loan.user.entity.User;
import com.microloan.Micro_loan.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NetworkBalanceService {

    private final ConnectionRequestRepository connectionRepository;
    private final MoneyTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public NetworkBalanceService(ConnectionRequestRepository connectionRepository,
                                 MoneyTransactionRepository transactionRepository,
                                 UserRepository userRepository) {
        this.connectionRepository = connectionRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public BalanceResponse calculate(String email) {
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Set<UUID> networkIds = Set.copyOf(connectionRepository.findConnectedUserIds(currentUser.getId()));
        Map<UUID, User> members = userRepository.findAllById(networkIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        List<MoneyTransaction> transactions = transactionRepository.findNetworkTransactions(
                networkIds, Status.CONFIRMED, Visibility.SHARED);

        Map<String, Map<UUID, BigDecimal>> netsByCurrency = new TreeMap<>();
        for (MoneyTransaction transaction : transactions) {
            Map<UUID, BigDecimal> net = netsByCurrency.computeIfAbsent(transaction.getCurrency(), ignored -> {
                Map<UUID, BigDecimal> initial = new LinkedHashMap<>();
                members.keySet().stream().sorted(Comparator.comparing(UUID::toString))
                        .forEach(id -> initial.put(id, BigDecimal.ZERO));
                return initial;
            });
            net.merge(transaction.getPayer().getId(), transaction.getAmount().negate(), BigDecimal::add);
            net.merge(transaction.getRecipient().getId(), transaction.getAmount(), BigDecimal::add);
        }

        List<CurrencyBalance> currencies = netsByCurrency.entrySet().stream()
                .map(entry -> toCurrencyBalance(entry.getKey(), entry.getValue(), members))
                .toList();
        return new BalanceResponse(currentUser.getId(), currencies);
    }

    private static CurrencyBalance toCurrencyBalance(String currency, Map<UUID, BigDecimal> net,
                                                     Map<UUID, User> members) {
        Comparator<UUID> byId = Comparator.comparing(UUID::toString);
        List<MemberBalance> memberBalances = net.entrySet().stream().sorted(Map.Entry.comparingByKey(byId))
                .map(entry -> new MemberBalance(entry.getKey(), members.get(entry.getKey()).getName(), entry.getValue()))
                .toList();

        List<UUID> debtors = net.entrySet().stream().filter(entry -> entry.getValue().signum() < 0)
                .map(Map.Entry::getKey).sorted(byId).collect(Collectors.toCollection(ArrayList::new));
        List<UUID> creditors = net.entrySet().stream().filter(entry -> entry.getValue().signum() > 0)
                .map(Map.Entry::getKey).sorted(byId).collect(Collectors.toCollection(ArrayList::new));
        Map<UUID, BigDecimal> remaining = new LinkedHashMap<>(net);
        List<SuggestedPayment> payments = new ArrayList<>();
        int debtorIndex = 0;
        int creditorIndex = 0;
        while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
            UUID debtorId = debtors.get(debtorIndex);
            UUID creditorId = creditors.get(creditorIndex);
            BigDecimal debt = remaining.get(debtorId).abs();
            BigDecimal due = remaining.get(creditorId);
            BigDecimal amount = debt.min(due);
            payments.add(new SuggestedPayment(debtorId, members.get(debtorId).getName(),
                    creditorId, members.get(creditorId).getName(), amount));
            remaining.put(debtorId, remaining.get(debtorId).add(amount));
            remaining.put(creditorId, remaining.get(creditorId).subtract(amount));
            if (remaining.get(debtorId).signum() == 0) debtorIndex++;
            if (remaining.get(creditorId).signum() == 0) creditorIndex++;
        }
        return new CurrencyBalance(currency, memberBalances, payments);
    }
}
