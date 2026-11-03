package com.microloan.Micro_loan.balance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microloan.Micro_loan.balance.dto.BalanceResponse;
import com.microloan.Micro_loan.balance.dto.BalanceResponse.CurrencyBalance;
import com.microloan.Micro_loan.balance.dto.BalanceResponse.Direction;
import com.microloan.Micro_loan.balance.dto.BalanceResponse.IncludedTransaction;
import com.microloan.Micro_loan.balance.dto.BalanceResponse.SuggestedPayment;
import com.microloan.Micro_loan.balance.entity.BalanceSnapshot;
import com.microloan.Micro_loan.balance.repository.BalanceRefreshLockRepository;
import com.microloan.Micro_loan.balance.repository.BalanceSnapshotRepository;
import com.microloan.Micro_loan.connection.entity.ConnectionRequest;
import com.microloan.Micro_loan.connection.repository.ConnectionRequestRepository;
import com.microloan.Micro_loan.transaction.entity.MoneyTransaction;
import com.microloan.Micro_loan.transaction.entity.MoneyTransaction.Status;
import com.microloan.Micro_loan.transaction.entity.MoneyTransaction.Visibility;
import com.microloan.Micro_loan.transaction.repository.MoneyTransactionRepository;
import com.microloan.Micro_loan.user.entity.User;
import com.microloan.Micro_loan.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class BalanceSnapshotService {

    private final ConnectionRequestRepository connectionRepository;
    private final MoneyTransactionRepository transactionRepository;
    private final BalanceSnapshotRepository snapshotRepository;
    private final BalanceRefreshLockRepository refreshLockRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BalanceSnapshotService(ConnectionRequestRepository connectionRepository,
                                  MoneyTransactionRepository transactionRepository,
                                  BalanceSnapshotRepository snapshotRepository,
                                  BalanceRefreshLockRepository refreshLockRepository,
                                  UserRepository userRepository) {
        this.connectionRepository = connectionRepository;
        this.transactionRepository = transactionRepository;
        this.snapshotRepository = snapshotRepository;
        this.refreshLockRepository = refreshLockRepository;
        this.userRepository = userRepository;
    }

    public BalanceResponse getBalance(String email) {
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        BalanceSnapshot snapshot = snapshotRepository.findByUserId(currentUser.getId()).orElse(null);
        if (snapshot == null) {
            refreshForUser(currentUser.getId());
            snapshot = snapshotRepository.findByUserId(currentUser.getId())
                    .orElseThrow(() -> new IllegalStateException("Balance snapshot was not created"));
        }
        return withCurrentNames(deserialize(snapshot.getSnapshotJson()), currentUser.getId());
    }

    public void refreshForUser(UUID userId) {
        refreshLockRepository.acquire();
        Set<UUID> component = new LinkedHashSet<>(connectionRepository.findConnectedUserIds(userId));
        rebuildComponent(component);
    }

    public void refreshComponentsFor(Collection<UUID> seedUserIds) {
        if (seedUserIds.isEmpty()) return;
        refreshLockRepository.acquire();
        Set<UUID> remainingSeeds = new LinkedHashSet<>(seedUserIds);
        while (!remainingSeeds.isEmpty()) {
            UUID seed = remainingSeeds.iterator().next();
            Set<UUID> component = new LinkedHashSet<>(connectionRepository.findConnectedUserIds(seed));
            if (component.isEmpty()) component.add(seed);
            remainingSeeds.removeAll(component);
            rebuildComponent(component);
        }
    }

    private void rebuildComponent(Set<UUID> componentIds) {
        if (componentIds.isEmpty()) return;
        Map<UUID, User> members = userRepository.findAllById(componentIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        List<MoneyTransaction> includedTransactions = transactionRepository.findNetworkTransactions(
                componentIds, Status.CONFIRMED, Visibility.SHARED);
        List<ConnectionRequest> networkConnections = connectionRepository.findConnectionsWithinNetwork(
                componentIds, com.microloan.Micro_loan.connection.entity.ConnectionRequest.Status.ACCEPTED);
        Map<String, Map<UUID, BigDecimal>> netsByCurrency = new TreeMap<>();
        for (MoneyTransaction transaction : includedTransactions) {
            Map<UUID, BigDecimal> nets = netsByCurrency.computeIfAbsent(transaction.getCurrency(), ignored -> {
                Map<UUID, BigDecimal> initial = new LinkedHashMap<>();
                members.keySet().stream().sorted(Comparator.comparing(UUID::toString))
                        .forEach(id -> initial.put(id, BigDecimal.ZERO));
                return initial;
            });
            nets.merge(transaction.getPayer().getId(), transaction.getAmount().negate(), BigDecimal::add);
            nets.merge(transaction.getRecipient().getId(), transaction.getAmount(), BigDecimal::add);
        }

        Map<UUID, List<CurrencyBalance>> personalCurrencies = new HashMap<>();
        members.keySet().forEach(id -> personalCurrencies.put(id, new ArrayList<>()));
        for (Map.Entry<String, Map<UUID, BigDecimal>> entry : netsByCurrency.entrySet()) {
            String currency = entry.getKey();
            Map<UUID, BigDecimal> nets = entry.getValue();
            List<SuggestedPayment> allPayments = suggestDirectPayments(nets, members, networkConnections);
            Map<UUID, List<MoneyTransaction>> transactionsByMember = new HashMap<>();
            for (MoneyTransaction transaction : includedTransactions) {
                if (!transaction.getCurrency().equals(currency)) continue;
                transactionsByMember.computeIfAbsent(transaction.getPayer().getId(), ignored -> new ArrayList<>()).add(transaction);
                transactionsByMember.computeIfAbsent(transaction.getRecipient().getId(), ignored -> new ArrayList<>()).add(transaction);
            }
            for (UUID memberId : members.keySet()) {
                List<MoneyTransaction> memberTransactions = transactionsByMember.getOrDefault(memberId, List.of());
                List<IncludedTransaction> breakdown = memberTransactions.stream()
                        .map(transaction -> toIncludedTransaction(transaction, memberId, members))
                        .toList();
                List<SuggestedPayment> personalPayments = allPayments.stream()
                        .filter(payment -> payment.fromUserId().equals(memberId) || payment.toUserId().equals(memberId))
                        .toList();
                if (breakdown.isEmpty() && personalPayments.isEmpty()) continue;
                BigDecimal paid = breakdown.stream().filter(item -> item.direction() == Direction.PAID)
                        .map(IncludedTransaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal received = breakdown.stream().filter(item -> item.direction() == Direction.RECEIVED)
                        .map(IncludedTransaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                personalCurrencies.get(memberId).add(new CurrencyBalance(currency, nets.get(memberId), paid,
                        received, breakdown, personalPayments));
            }
        }

        String calculatedAt = Instant.now().toString();
        Map<UUID, BalanceSnapshot> existingSnapshots = snapshotRepository.findAllByUserIdIn(componentIds).stream()
                .collect(Collectors.toMap(BalanceSnapshot::getUserId, snapshot -> snapshot));
        List<BalanceSnapshot> snapshots = new ArrayList<>();
        for (UUID memberId : members.keySet()) {
            BalanceResponse response = new BalanceResponse(memberId, calculatedAt,
                    personalCurrencies.getOrDefault(memberId, List.of()));
            BalanceSnapshot snapshot = existingSnapshots.getOrDefault(memberId, new BalanceSnapshot());
            snapshot.setUserId(memberId);
            snapshot.setSnapshotJson(serialize(response));
            snapshots.add(snapshot);
        }
        snapshotRepository.saveAll(snapshots);
    }

    private List<SuggestedPayment> suggestDirectPayments(Map<UUID, BigDecimal> nets, Map<UUID, User> members,
                                                         List<ConnectionRequest> connections) {
        Map<UUID, Set<UUID>> adjacency = new HashMap<>();
        members.keySet().forEach(id -> adjacency.put(id, new TreeSet<>(Comparator.comparing(UUID::toString))));
        for (ConnectionRequest connection : connections) {
            UUID firstId = connection.getRequester().getId();
            UUID secondId = connection.getRecipient().getId();
            adjacency.get(firstId).add(secondId);
            adjacency.get(secondId).add(firstId);
        }

        UUID rootId = members.keySet().stream().min(Comparator.comparing(UUID::toString)).orElseThrow();
        Map<UUID, UUID> parentByUser = new HashMap<>();
        List<UUID> traversalOrder = new ArrayList<>();
        Queue<UUID> queue = new ArrayDeque<>();
        parentByUser.put(rootId, null);
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID currentId = queue.remove();
            traversalOrder.add(currentId);
            for (UUID neighborId : adjacency.get(currentId)) {
                if (!parentByUser.containsKey(neighborId)) {
                    parentByUser.put(neighborId, currentId);
                    queue.add(neighborId);
                }
            }
        }

        if (traversalOrder.size() != members.size()) {
            throw new IllegalStateException("Accepted connections do not form the expected connected component");
        }

        Map<UUID, BigDecimal> subtreeNets = new LinkedHashMap<>(nets);
        List<SuggestedPayment> suggestions = new ArrayList<>();

        for (int index = traversalOrder.size() - 1; index > 0; index--) {
            UUID childId = traversalOrder.get(index);
            UUID parentId = parentByUser.get(childId);
            BigDecimal subtreeNet = subtreeNets.get(childId);
            if (subtreeNet.signum() < 0) {
                suggestions.add(new SuggestedPayment(childId, members.get(childId).getName(),
                        parentId, members.get(parentId).getName(), subtreeNet.abs()));
            } else if (subtreeNet.signum() > 0) {
                suggestions.add(new SuggestedPayment(parentId, members.get(parentId).getName(),
                        childId, members.get(childId).getName(), subtreeNet));
            }
            subtreeNets.merge(parentId, subtreeNet, BigDecimal::add);
        }
        return suggestions;
    }

    private IncludedTransaction toIncludedTransaction(MoneyTransaction transaction, UUID memberId,
                                                       Map<UUID, User> members) {
        boolean paid = transaction.getPayer().getId().equals(memberId);
        User counterparty = members.get(paid ? transaction.getRecipient().getId() : transaction.getPayer().getId());
        return new IncludedTransaction(transaction.getId(), paid ? Direction.PAID : Direction.RECEIVED,
                counterparty.getId(), counterparty.getName(), transaction.getAmount(), transaction.getCreatedAt().toString());
    }

    private BalanceResponse withCurrentNames(BalanceResponse stored, UUID currentUserId) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(currentUserId);
        for (CurrencyBalance currency : stored.currencies()) {
            currency.includedTransactions().forEach(item -> ids.add(item.counterpartyId()));
            currency.suggestedPayments().forEach(payment -> {
                ids.add(payment.fromUserId());
                ids.add(payment.toUserId());
            });
        }
        Map<UUID, User> currentUsers = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        List<CurrencyBalance> currentCurrencies = stored.currencies().stream().map(currency -> {
            List<IncludedTransaction> transactions = currency.includedTransactions().stream().map(item ->
                    new IncludedTransaction(item.transactionId(), item.direction(), item.counterpartyId(),
                            currentUsers.containsKey(item.counterpartyId()) ? currentUsers.get(item.counterpartyId()).getName() : item.counterpartyName(),
                            item.amount(), item.recordedAt())).toList();
            List<SuggestedPayment> payments = currency.suggestedPayments().stream().map(payment ->
                    new SuggestedPayment(payment.fromUserId(), currentUsers.containsKey(payment.fromUserId())
                            ? currentUsers.get(payment.fromUserId()).getName() : payment.fromName(),
                            payment.toUserId(), currentUsers.containsKey(payment.toUserId())
                            ? currentUsers.get(payment.toUserId()).getName() : payment.toName(), payment.amount())).toList();
            return new CurrencyBalance(currency.currency(), currency.netAmount(), currency.totalPaid(),
                    currency.totalReceived(), transactions, payments);
        }).toList();
        return new BalanceResponse(currentUserId, stored.calculatedAt(), currentCurrencies);
    }

    private BalanceResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, BalanceResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read stored balance snapshot", exception);
        }
    }

    private String serialize(BalanceResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store balance snapshot", exception);
        }
    }
}
